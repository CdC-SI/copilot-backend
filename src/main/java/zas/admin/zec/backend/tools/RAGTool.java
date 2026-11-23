package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.workspace.WorkspaceDto;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.ChatClientConfig;
import zas.admin.zec.backend.prompts.Prompt;
import zas.admin.zec.backend.prompts.PromptId;
import zas.admin.zec.backend.prompts.PromptRegistry;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.rag.TurnUsage;
import zas.admin.zec.backend.rag.remote.ZiaRagDocumentRetriever;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Tool Spring AI exposant la recherche documentaire (RAG) sur la documentation des assurances
 * sociales suisses. Le LLM agentique décide lui-même d'invoquer ce tool via tool-calling.
 *
 * <p>La requête de recherche est fournie par le LLM. Les données contextuelles non fournies par le
 * LLM (identité de l'utilisateur, langue, conversation) transitent par le {@link ToolContext} ; les
 * clés sont définies dans {@link ToolContextKeys}. Le workspace peut être fourni via le contexte
 * (mémorisé depuis un tour précédent de la conversation) ou, à défaut, est inféré ici à partir de
 * la question et de la liste des workspaces disponibles — une seule fois par tour : les appels
 * suivants du même tour réutilisent le workspace résolu
 * ({@link ToolContextKeys#CTX_RESOLVED_WORKSPACE}).</p>
 *
 * <p>La recherche est déléguée au service zia-rag (une requête par appel, limitée aux sources du workspace) :
 * mode de recherche, reranking et seuils relèvent de zia-rag ; la requête du LLM est transmise telle quelle (pas de
 * réécriture ni d'extension dans le backend).</p>
 */
@Slf4j
@Component
public class RAGTool {

    public static final String TOOL_NAME = "search_social_insurance_documentation";

    private static final String DEFAULT_LANGUAGE = "fr";

    /** Appel sans requête (le LLM a omis l'argument) : message exploitable plutôt qu'une exception. */
    static final String MISSING_QUERY = "<error>The 'query' argument is required: rephrase the user's question as a "
            + "standalone search query and call the tool again.</error>";

    private static final String NO_DOCS_FOUND = """
            <no_documentation_found>
            The knowledge base search returned NO relevant documents for this query.
            ABSOLUTE RULE: You MUST NOT answer this question — not from memory, not from general knowledge, not by inference.
            Your only permitted response is to inform the user that no relevant documentation was found and that you cannot answer without it.
            </no_documentation_found>
            """;

    private final ChatClient internalChatClient;
    private final SourceResolver sourceResolver;
    private final WorkspaceService workspaceService;
    private final WorkspaceInferenceMonitoringService workspaceInferenceMonitoringService;
    private final PromptRegistry promptRegistry;
    private final ZiaRagDocumentRetriever ziaRagRetriever;

    public RAGTool(
            @Qualifier(ChatClientConfig.INTERNAL_CHAT_CLIENT_BUILDER) ChatClient.Builder chatClientBuilder,
            SourceResolver sourceResolver,
            WorkspaceService workspaceService,
            WorkspaceInferenceMonitoringService workspaceInferenceMonitoringService,
            PromptRegistry promptRegistry,
            ZiaRagDocumentRetriever ziaRagRetriever) {

        this.internalChatClient = chatClientBuilder.build();
        this.sourceResolver = sourceResolver;
        this.workspaceService = workspaceService;
        this.workspaceInferenceMonitoringService = workspaceInferenceMonitoringService;
        this.promptRegistry = promptRegistry;
        this.ziaRagRetriever = ziaRagRetriever;
    }

    @Tool(name = TOOL_NAME, description = """
            Recherche dans la documentation officielle des assurances sociales suisses (AVS, AI, APG, PC, \
            allocations familiales, etc.) et dans les descriptions des processus métier internes les \
            passages pertinents pour répondre à une question. \
            À utiliser dès qu'une question porte sur les prestations, cotisations, réglementations, \
            procédures ou tout autre sujet relatif aux assurances sociales suisses. \
            Retourne les extraits pertinents, chacun encadré par une balise <document id="..." collection="..."> \
            et suivi de ses métadonnées JSON dans une balise <metadata> (doc_id, chunk_index, title, url, \
            outgoing_links, process_id...). Ces métadonnées permettent d'appeler get_documents pour suivre \
            un lien sortant, lire les chunks voisins ou ouvrir le contenu complet d'un processus métier.""")
    public String searchSocialInsuranceDocumentation(
            @ToolParam(description = """
                    La requête de recherche, reformulée en **phrase complète et autonome** (avec un verbe \
                    conjugué), reprenant les termes de la question de l'utilisateur et du contexte de la \
                    conversation, plutôt qu'une suite de mots-clés juxtaposés.""")
            String query,
            ToolContext toolContext) {

        Map<String, Object> context = toolContext != null ? toolContext.getContext() : Map.of();
        if (!ToolCallBudget.tryAcquire(context, TOOL_NAME)) {
            return ToolCallBudget.EXHAUSTED_MESSAGE;
        }
        if (query == null || query.isBlank()) {
            return MISSING_QUERY;
        }
        String userId = asString(context.get(ToolContextKeys.CTX_USER_ID), "");
        String language = asString(context.get(ToolContextKeys.CTX_LANGUAGE), DEFAULT_LANGUAGE);
        String conversationId = asString(context.get(ToolContextKeys.CTX_CONVERSATION_ID), "");

        // Le workspace peut déjà être connu : fourni avec la question (badge choisi par l'utilisateur,
        // mémorisé depuis un tour précédent), ou inféré par un appel précédent du même tour.
        String workspace = asString(context.get(ToolContextKeys.CTX_WORKSPACE), "");
        if (workspace.isBlank()) {
            workspace = previouslyInferredWorkspace(context);
        }
        // Sinon on l'infère à partir de la question et des workspaces disponibles (une seule fois par tour).
        if (workspace.isBlank()) {
            ToolContextKeys.emitStatus(context, ChatStatus.WORKSPACE_INFERENCE, language);
            workspace = inferWorkspace(query, language, context);

            // Monitoring : journaliser l'inférence (question originale de l'utilisateur, stable),
            // afin d'aider à améliorer la classification des workspaces pour les questions futures.
            String originalQuestion = asString(context.get(ToolContextKeys.CTX_ORIGINAL_QUESTION), "");
            workspaceInferenceMonitoringService.recordInference(userId, conversationId, originalQuestion, workspace);
        }
        depositResolvedWorkspace(context, workspace);

        // Notifier le frontend que la recherche documentaire est en cours.
        ToolContextKeys.emitStatus(context, ChatStatus.RETRIEVAL, language);

        List<Document> documents = ziaRagRetriever.retrieve(query, sourceResolver.resolve(workspace), userId,
                asString(context.get(ToolContextKeys.CTX_USER_TOKEN), null));

        // Remonter les documents (avec leurs métadonnées) à l'appelant pour reconstruire les sources.
        if (context.get(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS) instanceof Collection<?> sink) {
            @SuppressWarnings("unchecked")
            var documentSink = (Collection<Document>) sink;
            documentSink.addAll(documents);
        }

        log.debug("RAGTool retrieved {} documents for query '{}'", documents.size(), query);
        return formatDocuments(documents, context);
    }

    private String formatDocuments(List<Document> documents, Map<String, Object> context) {
        if (documents.isEmpty()) {
            return NO_DOCS_FOUND;
        }

        return documents.stream()
                .map(document -> MetadataFormatter.format(document, true, true,
                        ToolContextKeys.citationRef(context, document.getId())))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String asString(Object value, String defaultValue) {
        return value instanceof String s && !s.isBlank() ? s : defaultValue;
    }

    /**
     * Infère le workspace le plus pertinent pour la question posée, à partir de la description et
     * des questions hypothétiques de chaque workspace connu. Retombe sur le workspace par défaut
     * (catch-all, sans filtrage par source) si la réponse du LLM est vide, inconnue, ou en cas
     * d'erreur.
     */
    private String inferWorkspace(String query, String language, Map<String, Object> context) {
        String defaultWorkspace = workspaceService.getDefaultWorkspace();
        try {
            List<WorkspaceDto> workspaces = workspaceService.getAll();
            if (workspaces.isEmpty()) {
                return defaultWorkspace;
            }

            Prompt prompt = promptRegistry.get(PromptId.WORKSPACE_INFERENCE, language);
            ToolContextKeys.recordPrompt(context, prompt);
            String systemPrompt = prompt.render("workspaces", describeWorkspaces(workspaces),
                    "default_workspace", defaultWorkspace);

            ChatResponse response = internalChatClient.prompt()
                    .system(systemPrompt)
                    .user(query)
                    .call()
                    .chatResponse();
            recordUsage(context, "workspace_inference", response);
            String answer = response != null && response.getResult() != null
                    ? response.getResult().getOutput().getText() : null;

            String resolved = answer != null ? answer.trim() : "";
            boolean isKnown = workspaces.stream().anyMatch(w -> w.name().equals(resolved));
            if (isKnown) {
                return resolved;
            }

            log.debug("Réponse d'inférence de workspace inconnue ou vide ('{}'), repli sur '{}'", answer, defaultWorkspace);
            return defaultWorkspace;
        } catch (Exception e) {
            log.warn("Échec de l'inférence de workspace, repli sur '{}'", defaultWorkspace, e);
            return defaultWorkspace;
        }
    }

    /** Dépose l'usage d'un appel LLM du tool dans {@link ToolContextKeys#CTX_LLM_USAGE}, si l'appelant l'a fourni. */
    private static void recordUsage(Map<String, Object> context, String purpose, ChatResponse response) {
        var call = TurnUsage.LlmCall.from(purpose, response);
        if (!(context.get(ToolContextKeys.CTX_LLM_USAGE) instanceof Collection<?> sink) || call == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        var calls = (Collection<TurnUsage.LlmCall>) sink;
        calls.add(call);
    }

    private String describeWorkspaces(List<WorkspaceDto> workspaces) {
        return workspaces.stream()
                .map(w -> "- %s : %s%s".formatted(
                        w.name(),
                        w.description() != null && !w.description().isBlank() ? w.description() : "(pas de description)",
                        w.hypotheticalQuestions().isEmpty()
                                ? ""
                                : " Exemples de questions : " + String.join(" | ", w.hypotheticalQuestions())))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Workspace déjà résolu au cours du tour par un appel précédent à ce tool, ou chaîne vide si aucun.
     *
     * <p>La référence est partagée par tous les appels du tour (clé
     * {@link ToolContextKeys#CTX_RESOLVED_WORKSPACE}) : sans elle, chaque appel de recherche relancerait
     * sa propre inférence — un appel LLM pour chaque requête reformulée par le LLM agentique, avec un
     * risque de filtres différents au sein d'une même réponse.</p>
     */
    static String previouslyInferredWorkspace(Map<String, Object> context) {
        if (context.get(ToolContextKeys.CTX_RESOLVED_WORKSPACE) instanceof AtomicReference<?> ref
                && ref.get() instanceof String workspace) {
            return workspace;
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private void depositResolvedWorkspace(Map<String, Object> context, String workspace) {
        if (context.get(ToolContextKeys.CTX_RESOLVED_WORKSPACE) instanceof AtomicReference<?> ref) {
            ((AtomicReference<String>) ref).set(workspace);
        }
    }
}

