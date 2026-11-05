package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.properties.AgentProperties;
import zas.admin.zec.backend.config.properties.DocumentToolProperties;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository.AttachmentSummary;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.TurnMetaStreamFilter;
import zas.admin.zec.backend.rag.TurnStatus;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.TurnUsage;
import zas.admin.zec.backend.rag.UsageTracker;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.token.TurnTraceToken;
import zas.admin.zec.backend.rag.token.WorkspaceToken;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;
import zas.admin.zec.backend.tools.DocumentTool;
import zas.admin.zec.backend.tools.LinkedProcessAttacher;
import zas.admin.zec.backend.tools.RAGTool;
import zas.admin.zec.backend.tools.ToolCallRecorder;
import zas.admin.zec.backend.tools.ToolContextKeys;
import zas.admin.zec.backend.tools.WorkspaceInferenceMonitoringService;
import zas.admin.zec.backend.tools.document.DocumentCollection;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stratégie {@link ChatService} "fonctionnalités complètes" : un unique {@link ChatClient} auquel
 * sont attribués les tools {@link RAGTool} (recherche documentaire sémantique), {@link DocumentTool}
 * (récupération exacte par métadonnées : liens sortants, chunks voisins, processus métier) et
 * {@link ConversationAttachmentTool} (pièces jointes). Le LLM décide lui-même d'invoquer ces tools via
 * tool-calling, sauf lorsque le workspace est fourni explicitement dans la question (correction d'une
 * inférence précédente) : une consigne prioritaire est alors ajoutée au message utilisateur courant pour rendre
 * l'appel à {@link RAGTool} obligatoire (et non au system prompt, qui reste identique d'un tour à l'autre).
 *
 * <p>Active pour les conversations de type {@link ConversationType#COMPLETE}.</p>
 *
 * <p>Les données non fournies par le LLM (userId, langue, workspace, conversationId, budget d'appels)
 * transitent par le {@code ToolContext}. Les documents récupérés par les tools sont collectés via une
 * liste partagée ; en fin de réponse, la balise {@code <turn_meta>} déclarée par l'agent (retirée du
 * flux) détermine les {@link SourceToken} affichés ({@link SourceDisplayPolicy}), et la trace du tour
 * est transmise pour persistance ({@link TurnTraceToken}), avec le journal des appels de tools
 * ({@link ToolCallRecorder}).</p>
 *
 * <p>Mémoire inter-tours : chaque message utilisateur de l'historique, ainsi que la question courante,
 * est préfixé par le bloc {@code <retrieved_sources>} des documents consultés au tour précédent. La question
 * courante est de plus préfixée par le bloc {@code <conversation_attachments>} (fichiers attachés, sans contenu) :
 * l'agent les lit lui-même via {@code get_attachment_content}. Quand le tour précédent s'appuyait sur un processus
 * (question de suivi, ou réponse qui le cite dans {@code used}), elle est aussi préfixée par le bloc
 * {@code <linked_processes>} : la réponse de l'utilisateur est ainsi appliquée au processus sans dépendre d'une
 * relecture décidée par le LLM.</p>
 */
@Slf4j
@Service
public class RAGChatService extends AbstractChatService {

    private final ChatClient internalChatClient;
    /** Callbacks des tools (sans état), créés une fois ; enveloppés à chaque tour par le {@link ToolCallRecorder}. */
    private final ToolCallback[] toolCallbacks;
    private final WorkspaceInferenceMonitoringService workspaceInferenceMonitoringService;
    private final DocumentToolProperties documentToolProperties;
    private final AttachmentRepository attachmentRepository;
    private final LinkedProcessAttacher linkedProcessAttacher;
    private final UserService userService;
    private final AgentProperties agentProperties;

    public RAGChatService(@Qualifier("internalChatModel") ChatModel internalChatModel,
                          RAGTool ragTool,
                          DocumentTool documentTool,
                          ConversationAttachmentTool attachmentTool,
                          WorkspaceInferenceMonitoringService workspaceInferenceMonitoringService,
                          DocumentToolProperties documentToolProperties,
                          AttachmentRepository attachmentRepository,
                          LinkedProcessAttacher linkedProcessAttacher,
                          UserService userService,
                          AgentProperties agentProperties) {
        this.internalChatClient = ChatClient.create(internalChatModel);
        this.toolCallbacks = ToolCallbacks.from(ragTool, attachmentTool, documentTool);
        this.workspaceInferenceMonitoringService = workspaceInferenceMonitoringService;
        this.documentToolProperties = documentToolProperties;
        this.attachmentRepository = attachmentRepository;
        this.linkedProcessAttacher = linkedProcessAttacher;
        this.userService = userService;
        this.agentProperties = agentProperties;
    }

    @Override
    public boolean supports(ConversationType conversationType) {
        return conversationType == ConversationType.COMPLETE;
    }

    @Override
    public Flux<Token> answer(Question question, String userId, List<Message> conversationHistory) {
        // Liste partagée (thread-safe) que le tool alimentera lors d'un éventuel appel.
        List<Document> retrievedDocuments = new CopyOnWriteArrayList<>();

        // Sink partagé (thread-safe) dans lequel les tools émettent des StatusToken
        // avant/pendant leur traitement, pour notifier le frontend en temps réel.
        Sinks.Many<Token> statusSink = Sinks.many().unicast().onBackpressureBuffer();

        // Référence partagée dans laquelle RAGTool dépose le workspace effectivement utilisé
        // (connu dès le départ, ou inféré s'il était absent du contexte).
        AtomicReference<String> resolvedWorkspace = new AtomicReference<>();

        Map<String, Object> toolContext = baseToolContext(question, userId, statusSink);
        toolContext.put(ToolContextKeys.CTX_WORKSPACE, question.workspace());
        toolContext.put(ToolContextKeys.CTX_ORIGINAL_QUESTION, question.query());
        toolContext.put(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, retrievedDocuments);
        toolContext.put(ToolContextKeys.CTX_RESOLVED_WORKSPACE, resolvedWorkspace);
        toolContext.put(ToolContextKeys.CTX_TOOL_CALL_BUDGET,
                new AtomicInteger(documentToolProperties.maxToolCallsPerTurn()));

        // Extrait (et masque) la balise <turn_meta> de fin de réponse.
        TurnMetaStreamFilter turnMetaFilter = new TurnMetaStreamFilter();

        // Journal des appels de tools du tour (persisté avec la réponse).
        ToolCallRecorder toolCallRecorder = new ToolCallRecorder(retrievedDocuments);

        // Tokens du tour : appels de la boucle agentique (flux) et appels LLM faits par les tools.
        UsageTracker usageTracker = new UsageTracker();
        List<TurnUsage.LlmCall> auxiliaryUsage = new CopyOnWriteArrayList<>();
        toolContext.put(ToolContextKeys.CTX_LLM_USAGE, auxiliaryUsage);

        // Un workspace explicitement attaché à la question signale que l'utilisateur a corrigé
        // l'inférence précédente et relance la même question : on renseigne le workspace corrigé
        // dans la ligne de monitoring correspondante, et la recherche documentaire devient obligatoire.
        boolean workspaceProvided = question.workspace() != null && !question.workspace().isBlank();
        if (workspaceProvided) {
            workspaceInferenceMonitoringService.recordCorrection(
                    userId, question.conversationId(), question.query(), question.workspace());
        }

        Flux<Token> textTokens = internalChatClient
                .prompt()
                .system(agenticSystemPrompt(question))
                .options(agentOptions())
                .messages(toPromptMessages(conversationHistory))
                .toolCallbacks(toolCallRecorder.wrap(toolCallbacks))
                .toolContext(toolContext)
                .user(currentUserMessage(question, userId, conversationHistory, workspaceProvided, retrievedDocuments))
                .stream()
                .chatResponse()
                .doOnNext(usageTracker::accept)
                .map(response -> turnMetaFilter.accept(extractText(response)))
                // La balise <turn_meta> est retirée du flux ; le reliquat éventuel est émis en fin de flux.
                .concatWith(Mono.fromSupplier(turnMetaFilter::flush))
                .filter(text -> !text.isEmpty())
                .<Token>map(TextToken::new)
                .doOnComplete(() -> {
                    // La consigne de prompt n'offre pas de garantie stricte : on trace les cas où le
                    // LLM a malgré tout ignoré la recherche documentaire.
                    if (workspaceProvided && resolvedWorkspace.get() == null) {
                        log.warn("Workspace '{}' fourni explicitement mais RAGTool non appelé (conversation {})",
                                question.workspace(), question.conversationId());
                    }
                })
                .doFinally(signal -> statusSink.tryEmitComplete());

        // Les sources ne sont connues qu'après la génération : elles dépendent des documents consultés
        // par les tools et du statut de fin de tour déclaré par l'agent (<turn_meta>).
        Flux<Token> sourceTokens = Flux.defer(() -> {
            var outcome = SourceDisplayPolicy.decide(
                    turnMetaFilter.meta(),
                    retrievedDocuments.stream().map(ConsultedSource::from).toList(),
                    conversationHistory,
                    question.conversationId());
            return Flux.<Token>fromIterable(outcome.displayed())
                    .concatWithValues(new TurnTraceToken(outcome.trace()
                            .withToolCalls(toolCallRecorder.calls())
                            .withUsage(new TurnUsage(usageTracker.agentCalls(), auxiliaryUsage, agentSettings()))));
        });

        // De même pour le workspace résolu par RAGTool, s'il a été appelé.
        Flux<Token> workspaceToken = Flux.defer(() -> resolvedWorkspace.get() != null
                ? Flux.just(new WorkspaceToken(resolvedWorkspace.get()))
                : Flux.empty());

        // statusSink.asFlux() émet les StatusToken produits pendant le tool-calling,
        // avant et pendant que textTokens streame la réponse du LLM.
        return statusSink.asFlux().mergeWith(textTokens).concatWith(sourceTokens).concatWith(workspaceToken);
    }

    /** Pièces jointes de la conversation (sans contenu), annoncées à l'agent via {@code <conversation_attachments>}. */
    private List<AttachmentSummary> conversationAttachments(Question question, String userId) {
        if (question.conversationId() == null || question.conversationId().isBlank() || userId == null || userId.isBlank()) {
            return List.of();
        }
        return attachmentRepository.findSummariesByConversationIdAndUserIdOrderByIdAsc(question.conversationId(), userId);
    }

    /**
     * Question courante préfixée des blocs ajoutés par le système, dans cet ordre : {@code <retrieved_sources>} du
     * tour précédent, {@code <linked_processes>} du tour précédent, {@code <conversation_attachments>},
     * puis, si l'utilisateur a choisi le workspace, la consigne prioritaire de recherche obligatoire. Ces blocs ne
     * sont pas persistés et ne concernent que le dernier message : le system prompt et l'historique restent
     * identiques d'un tour à l'autre (prefix caching). Les processus joints sont ajoutés à {@code retrievedDocuments}.
     */
    String currentUserMessage(Question question, String userId, List<Message> conversationHistory,
                              boolean workspaceProvided, Collection<Document> retrievedDocuments) {
        String directive = workspaceProvided
                ? RAGPrompts.getForcedRetrievalDirective(question.language(), question.workspace()).strip()
                : "";
        String message = withContextBlock(directive, question.query());
        message = withContextBlock(ConversationAttachmentsRenderer.render(conversationAttachments(question, userId)), message);
        message = withContextBlock(processesFromPreviousTurn(question, userId, conversationHistory, retrievedDocuments), message);
        return withContextBlock(retrievedSourcesForCurrentTurn(conversationHistory), message);
    }

    /**
     * Bloc {@code <linked_processes>} des processus sur lesquels s'appuyait le tour précédent : tous les processus lus
     * après une question de suivi ({@code FOLLOWUP}), ceux déclarés dans {@code used} après une réponse ({@code ANSWER},
     * qui peut se terminer par une question : la question courante y répond alors probablement). Rien après un refus.
     */
    private String processesFromPreviousTurn(Question question, String userId, List<Message> conversationHistory,
                                             Collection<Document> retrievedDocuments) {
        if (conversationHistory.isEmpty() || "USER".equals(conversationHistory.getLast().role())) {
            return "";
        }
        TurnTrace trace = conversationHistory.getLast().turnTrace();
        if (trace == null || trace.status() == TurnStatus.REFUSAL) {
            return "";
        }
        List<String> used = trace.used() != null ? trace.used().declared() : List.of();
        List<String> processIds = trace.sources().stream()
                .filter(s -> DocumentCollection.BUSINESS_PROCESS.key().equals(s.collection()) && s.id() != null)
                .map(ConsultedSource::id)
                .filter(id -> trace.status() == TurnStatus.FOLLOWUP || used.contains(id))
                .toList();
        if (processIds.isEmpty()) {
            return "";
        }
        boolean hasInternalAccess = userId != null && !userId.isBlank() && userService.hasAccessToInternalDocuments(userId);
        var attachment = linkedProcessAttacher.forFollowUp(processIds, userId, hasInternalAccess, question.language());
        retrievedDocuments.addAll(attachment.documents());
        return LinkedProcessAttacher.format(attachment);
    }

    private String agenticSystemPrompt(Question question) {
        // Prompt non orienté RAG : le LLM décide lui-même d'appeler le tool de recherche
        // documentaire, et répond directement aux demandes situationnelles (résumé, traduction...).
        return RAGPrompts.getAgenticSystemPrompt(question.language())
                .formatted(question.responseFormat());
    }

    /**
     * Réglages propres à l'appel agent, combinés par le {@link ChatClient} avec les options par défaut du modèle
     * (modèle, clé, maxTokens…) : les appels internes au même modèle (choix de l'espace, réécriture) n'en héritent pas.
     */
    private OpenAiChatOptions.Builder agentOptions() {
        return OpenAiChatOptions.builder()
                .reasoningEffort(agentProperties.reasoningEffort())
                .temperature(agentProperties.temperature());
    }

    /** Réglages de l'appel agent, tracés avec l'usage du tour pour comparer les configurations à l'évaluation. */
    private Map<String, Object> agentSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("prompt_version", RAGPrompts.AGENTIC_PROMPT_VERSION);
        settings.put("reasoning_effort", agentProperties.reasoningEffort());
        settings.put("temperature", agentProperties.temperature());
        return settings;
    }

}


