package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.config.properties.DocumentToolProperties;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository.AttachmentSummary;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.TurnMetaStreamFilter;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.token.TurnTraceToken;
import zas.admin.zec.backend.rag.token.WorkspaceToken;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;
import zas.admin.zec.backend.tools.DocumentTool;
import zas.admin.zec.backend.tools.RAGTool;
import zas.admin.zec.backend.tools.ToolContextKeys;
import zas.admin.zec.backend.tools.WorkspaceInferenceMonitoringService;

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
 * inférence précédente) : une consigne prioritaire est alors ajoutée au system prompt pour rendre
 * l'appel à {@link RAGTool} obligatoire.
 *
 * <p>Active pour les conversations de type {@link ConversationType#COMPLETE}.</p>
 *
 * <p>Les données non fournies par le LLM (userId, langue, workspace, conversationId, budget d'appels)
 * transitent par le {@code ToolContext}. Les documents récupérés par les tools sont collectés via une
 * liste partagée ; en fin de réponse, la balise {@code <turn_meta>} déclarée par l'agent (retirée du
 * flux) détermine les {@link SourceToken} affichés ({@link SourceDisplayPolicy}), et la trace du tour
 * est transmise pour persistance ({@link TurnTraceToken}).</p>
 *
 * <p>Mémoire inter-tours : chaque message utilisateur de l'historique, ainsi que la question courante,
 * est préfixé par le bloc {@code <retrieved_sources>} des documents consultés au tour précédent. La question
 * courante est de plus préfixée par le bloc {@code <conversation_attachments>} (fichiers attachés, sans contenu) :
 * l'agent les lit lui-même via {@code get_attachment_content}.</p>
 */
@Slf4j
@Service
public class RAGChatService extends AbstractChatService {

    private final ChatClient internalChatClient;
    private final RAGTool ragTool;
    private final DocumentTool documentTool;
    private final ConversationAttachmentTool attachmentTool;
    private final WorkspaceInferenceMonitoringService workspaceInferenceMonitoringService;
    private final DocumentToolProperties documentToolProperties;
    private final AttachmentRepository attachmentRepository;

    public RAGChatService(@Qualifier("internalChatModel") ChatModel internalChatModel,
                          RAGTool ragTool,
                          DocumentTool documentTool,
                          ConversationAttachmentTool attachmentTool,
                          WorkspaceInferenceMonitoringService workspaceInferenceMonitoringService,
                          DocumentToolProperties documentToolProperties,
                          AttachmentRepository attachmentRepository) {
        this.internalChatClient = ChatClient.create(internalChatModel);
        this.ragTool = ragTool;
        this.documentTool = documentTool;
        this.attachmentTool = attachmentTool;
        this.workspaceInferenceMonitoringService = workspaceInferenceMonitoringService;
        this.documentToolProperties = documentToolProperties;
        this.attachmentRepository = attachmentRepository;
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
                .system(agenticSystemPrompt(question, workspaceProvided))
                .messages(toPromptMessages(conversationHistory))
                .tools(ragTool, attachmentTool, documentTool)
                .toolContext(toolContext)
                .user(withContextBlock(retrievedSourcesForCurrentTurn(conversationHistory),
                        withContextBlock(ConversationAttachmentsRenderer.render(conversationAttachments(question, userId)),
                                question.query())))
                .stream()
                .chatResponse()
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
                    .concatWithValues(new TurnTraceToken(outcome.trace()));
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

    private String agenticSystemPrompt(Question question, boolean workspaceProvided) {
        // Prompt non orienté RAG : le LLM décide lui-même d'appeler le tool de recherche
        // documentaire, et répond directement aux demandes situationnelles (résumé, traduction...).
        // Si le workspace est fourni explicitement, une consigne prioritaire rend l'appel obligatoire.
        String prompt = RAGPrompts.getAgenticSystemPrompt(question.language())
                .formatted(question.responseFormat());
        return workspaceProvided
                ? prompt + RAGPrompts.getForcedRetrievalDirective(question.language(), question.workspace())
                : prompt;
    }

}


