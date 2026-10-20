package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.workspace.WorkspaceDto;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.RetrievingProperties;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.TurnUsage;
import zas.admin.zec.backend.rag.joiner.RankedDocumentJoiner;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;
import zas.admin.zec.backend.rag.retriever.BM25DocumentRetriever;
import zas.admin.zec.backend.rag.retriever.HybridDocumentRetriever;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Tool Spring AI exposant la recherche documentaire (RAG) sur la documentation des assurances
 * sociales suisses. Le LLM agentique décide lui-même d'invoquer ce tool via tool-calling.
 *
 * <p>La requête de recherche est fournie par le LLM. Les données contextuelles non fournies par le
 * LLM (identité de l'utilisateur, langue, conversation) transitent par le {@link ToolContext} ; les
 * clés sont définies dans {@link ToolContextKeys}. Le workspace peut être fourni via le contexte
 * (mémorisé depuis un tour précédent de la conversation) ou, à défaut, est inféré ici à partir de
 * la question et de la liste des workspaces disponibles.</p>
 */
@Slf4j
@Component
public class RAGTool {

    public static final String TOOL_NAME = "search_social_insurance_documentation";

    private static final String DEFAULT_LANGUAGE = "fr";

    private static final String META_SOURCE = "source";
    private static final String META_ORGANIZATIONS = "organizations";
    private static final String META_USER_UUID = "user_uuid";
    private static final String ORG_ZAS = "ZAS";
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
    private final VectorStore documentStore;
    private final UserService userService;
    private final DocumentReranker reranker;
    private final RetrievingProperties retrievingProperties;
    private final JdbcTemplate jdbcTemplate;
    private final SourceResolver sourceResolver;
    private final WorkspaceService workspaceService;
    private final WorkspaceInferenceMonitoringService workspaceInferenceMonitoringService;

    public RAGTool(
            @Qualifier("internalChatModel") ChatModel internalChatModel,
            VectorStore documentStore,
            UserService userService,
            DocumentReranker reranker,
            RetrievingProperties retrievingProperties,
            JdbcTemplate jdbcTemplate,
            SourceResolver sourceResolver,
            WorkspaceService workspaceService,
            WorkspaceInferenceMonitoringService workspaceInferenceMonitoringService) {

        this.internalChatClient = ChatClient.create(internalChatModel);
        this.documentStore = documentStore;
        this.userService = userService;
        this.reranker = reranker;
        this.retrievingProperties = retrievingProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.sourceResolver = sourceResolver;
        this.workspaceService = workspaceService;
        this.workspaceInferenceMonitoringService = workspaceInferenceMonitoringService;
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
                    La requête de recherche, reformulée de façon autonome et explicite à partir de la \
                    question de l'utilisateur et du contexte de la conversation.""")
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

        // Le workspace peut déjà être connu (mémorisé depuis un tour précédent de la conversation) ;
        // sinon on l'infère à partir de la question et des workspaces disponibles.
        String workspace = asString(context.get(ToolContextKeys.CTX_WORKSPACE), "");
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

        boolean hasAccessToInternalDocuments = !userId.isBlank()
                && userService.hasAccessToInternalDocuments(userId);

        List<Document> documents = retrieve(query, language, workspace, userId, hasAccessToInternalDocuments);

        // Remonter les documents (avec leurs métadonnées) à l'appelant pour reconstruire les sources.
        if (context.get(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS) instanceof Collection<?> sink) {
            @SuppressWarnings("unchecked")
            var documentSink = (Collection<Document>) sink;
            documentSink.addAll(documents);
        }

        log.debug("RAGTool retrieved {} documents for query '{}'", documents.size(), query);
        return formatDocuments(documents);
    }

    private List<Document> retrieve(String query, String language, String workspace,
                                    String userId, boolean hasAccessToInternalDocuments) {

        Supplier<Filter.Expression> filterExpressionSupplier =
                () -> buildExpression(workspace, userId, hasAccessToInternalDocuments);

        var vectorRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(documentStore)
                .filterExpression(filterExpressionSupplier)
                .topK(retrievingProperties.topK())
                .build();

        DocumentRetriever documentRetriever = getDocumentRetriever(filterExpressionSupplier, vectorRetriever);

        // 1. Transform the (already autonomous) query.
        Query transformedQuery = Query.builder().text(query).build();
        for (var queryTransformer : transformers(language)) {
            transformedQuery = queryTransformer.apply(transformedQuery);
        }

        // 2. Expand the query into one or multiple queries.
        List<Query> expandedQueries = expander(language).expand(transformedQuery);

        // 3. Retrieve documents for each query.
        Map<Query, List<List<Document>>> documentsForQuery = new HashMap<>();
        for (Query expandedQuery : expandedQueries) {
            documentsForQuery.put(expandedQuery, List.of(documentRetriever.retrieve(expandedQuery)));
        }

        // 4. Join and rerank. Les pièces jointes de la conversation ne sont pas ajoutées aux résultats : l'agent
        //    les lit via get_attachment_content (liste fournie par le bloc <conversation_attachments>).
        var documentJoiner = new RankedDocumentJoiner(reranker);
        return documentJoiner.join(documentsForQuery);
    }

    private String formatDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return NO_DOCS_FOUND;
        }

        return documents.stream()
                .map(document -> MetadataFormatter.format(document, true, true))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private DocumentRetriever getDocumentRetriever(Supplier<Filter.Expression> filterExpressionSupplier,
                                                   VectorStoreDocumentRetriever vectorRetriever) {
        if (retrievingProperties.bm25().enabled()) {
            var bm25Retriever = new BM25DocumentRetriever(
                    jdbcTemplate,
                    retrievingProperties.bm25().topK(),
                    filterExpressionSupplier
            );
            return new HybridDocumentRetriever(vectorRetriever, bm25Retriever);
        }
        return vectorRetriever;
    }

    private Filter.Expression buildExpression(String workspace, String userId, boolean userHasAccessToInternalDocuments) {
        return metadataFilter(sourceResolver.resolve(workspace), userId, userHasAccessToInternalDocuments);
    }

    /**
     * Filtre sur les métadonnées : sources du workspace, exclusion des documents internes ({@code organizations}
     * contenant {@code ZAS}, valeur scalaire ou tableau) sauf droits, documents publics ou propres à l'utilisateur.
     */
    public static Filter.Expression metadataFilter(Collection<String> sources, String userId,
                                                   boolean userHasAccessToInternalDocuments) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();

        if (!CollectionUtils.isEmpty(sources)) {
            ops.add(builder.in(META_SOURCE, List.copyOf(sources)));
        }

        if (!userHasAccessToInternalDocuments) {
            // ne() seul ne suffit pas : sur un tableau, "!=" est vrai dès qu'un élément diffère (["ZAS","OFAS"]
            // passerait) ; nin() exclut tout tableau contenant ZAS. ne() conserve l'exclusion historique des
            // documents sans clé "organizations" (isNotNull() n'est pas supporté par le converter PgVector).
            ops.add(builder.and(builder.ne(META_ORGANIZATIONS, ORG_ZAS), builder.nin(META_ORGANIZATIONS, ORG_ZAS)));
        }

        ops.add(builder.group(builder.or(builder.eq(META_USER_UUID, userId), builder.eq(META_USER_UUID, ""))));

        FilterExpressionBuilder.Op combined = ops.getFirst();
        for (int i = 1; i < ops.size(); i++) {
            combined = builder.and(combined, ops.get(i));
        }

        return combined.build();
    }

    private List<QueryTransformer> transformers(String language) {
        var transformers = new ArrayList<QueryTransformer>();
        // Note: la compression basée sur l'historique est désormais à la charge du LLM agentique,
        // qui doit fournir une requête autonome. Seul le réécriture est conservée ici.
        if (retrievingProperties.queryRewriter().enabled()) {
            transformers.add(rewriter(language, internalChatClient));
        }
        return transformers;
    }

    private QueryTransformer rewriter(String lang, ChatClient client) {
        return RewriteQueryTransformer.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate(new PromptTemplate(
                        RAGPrompts.getQueryRewriterTemplate(lang)
                ))
                .build();
    }

    private QueryExpander expander(String lang) {
        if (!retrievingProperties.queryExpander().enabled()) {
            return (List::of);
        }

        return MultiQueryExpander.builder()
                .chatClientBuilder(internalChatClient.mutate())
                .promptTemplate(new PromptTemplate(
                        RAGPrompts.getQueryExpanderTemplate(lang)
                ))
                .numberOfQueries(retrievingProperties.queryExpander().numberOfExpansions())
                .includeOriginal(retrievingProperties.queryExpander().includeOriginal())
                .build();
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

            String systemPrompt = RAGPrompts.getWorkspaceInferenceTemplate(language)
                    .formatted(describeWorkspaces(workspaces), defaultWorkspace);

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
        if (!(context.get(ToolContextKeys.CTX_LLM_USAGE) instanceof Collection<?> sink) || response == null
                || response.getMetadata() == null || response.getMetadata().getUsage() == null
                || response.getMetadata().getUsage().getPromptTokens() == null) {
            return;
        }
        var usage = response.getMetadata().getUsage();
        @SuppressWarnings("unchecked")
        var calls = (Collection<TurnUsage.LlmCall>) sink;
        calls.add(new TurnUsage.LlmCall(purpose, usage.getPromptTokens(),
                usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0, usage.getCacheReadInputTokens()));
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

    @SuppressWarnings("unchecked")
    private void depositResolvedWorkspace(Map<String, Object> context, String workspace) {
        if (context.get(ToolContextKeys.CTX_RESOLVED_WORKSPACE) instanceof AtomicReference<?> ref) {
            ((AtomicReference<String>) ref).set(workspace);
        }
    }
}

