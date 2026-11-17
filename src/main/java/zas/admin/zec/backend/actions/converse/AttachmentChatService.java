package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.config.ChatClientConfig;
import zas.admin.zec.backend.prompts.Prompt;
import zas.admin.zec.backend.prompts.PromptId;
import zas.admin.zec.backend.prompts.PromptRegistry;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.TurnUsage;
import zas.admin.zec.backend.rag.UsageTracker;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.token.TurnTraceToken;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;
import zas.admin.zec.backend.tools.ToolApprovalService;
import zas.admin.zec.backend.tools.ToolCallRecorder;
import zas.admin.zec.backend.tools.ToolContextKeys;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stratégie {@link ChatService} "sans RAG" : un {@link ChatClient} auquel seul le tool
 * {@link ConversationAttachmentTool} (pièces jointes) est attribué. Aucune recherche documentaire
 * n'est effectuée : le LLM se comporte comme un assistant généraliste (rédaction, relecture,
 * traduction, reformulation, résumé, exploitation des pièces jointes...) et répond à partir de ses
 * connaissances générales, sans source officielle. Il reste discret sur les sujets internes ou
 * relevant du 1er pilier des assurances sociales (cf. {@link PromptId#ATTACHMENT_SYSTEM}).
 *
 * <p>Active pour les conversations de type {@link ConversationType#NO_RAG}.</p>
 *
 * <p>Version allégée par rapport à {@link RAGChatService} : pas de documents récupérés, pas de
 * workspace résolu, donc pas de {@link zas.admin.zec.backend.rag.token.SourceToken} ni de
 * {@link zas.admin.zec.backend.rag.token.WorkspaceToken} en sortie.</p>
 */
@Slf4j
@Service
public class AttachmentChatService extends AbstractChatService {

    private final ChatClient internalChatClient;
    private final PromptRegistry promptRegistry;
    private final ToolCallback[] toolCallbacks;
    private final ToolApprovalService toolApprovalService;

    public AttachmentChatService(@Qualifier(ChatClientConfig.INTERNAL_CHAT_CLIENT_BUILDER) ChatClient.Builder chatClientBuilder,
                                  ConversationAttachmentTool attachmentTool,
                                  ToolApprovalService toolApprovalService,
                                  PromptRegistry promptRegistry) {
        this.internalChatClient = chatClientBuilder.build();
        this.toolCallbacks = ToolCallbacks.from(attachmentTool);
        this.toolApprovalService = toolApprovalService;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public boolean supports(ConversationType conversationType) {
        return conversationType == ConversationType.NO_RAG;
    }

    @Override
    public Flux<Token> answer(Question question, String userId, List<Message> conversationHistory) {
        return answer(question, userId, conversationHistory, new AtomicReference<>());
    }

    @Override
    public Flux<Token> answer(Question question, String userId, List<Message> conversationHistory,
                              AtomicReference<ToolCallRecorder> recorderReference) {
        // Sink partagé (thread-safe) dans lequel le tool émet des StatusToken (ex. OCR)
        // avant/pendant son traitement, pour notifier le frontend en temps réel.
        Sinks.Many<Token> statusSink = Sinks.many().unicast().onBackpressureBuffer();

        Map<String, Object> toolContext = baseToolContext(question, userId, statusSink);
        Prompt systemPrompt = attachmentSystemPrompt(question);
        UsageTracker usageTracker = new UsageTracker();
        List<Document> retrievedDocuments = new CopyOnWriteArrayList<>();
        toolContext.put(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, retrievedDocuments);
        // Événements tool_call / tool_result et approbation des tools qui l'exigent (T1).
        ToolCallRecorder toolCallRecorder = new ToolCallRecorder(retrievedDocuments, toolApprovalService);
        recorderReference.set(toolCallRecorder);

        Flux<Token> textTokens = internalChatClient
                .prompt()
                .system(systemPrompt.render("response_format", question.responseFormat()))
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .toolCallbacks(toolCallRecorder.wrap(toolCallbacks))
                .toolContext(toolContext)
                .user(question.query())
                .stream()
                .chatResponse()
                .doOnNext(usageTracker::accept)
                .flatMap(this::toTextToken)
                .doFinally(signal -> statusSink.tryEmitComplete());

        // statusSink.asFlux() émet les StatusToken produits pendant le tool-calling,
        // avant et pendant que textTokens streame la réponse du LLM.
        Token traceToken = new TurnTraceToken(TurnTrace.empty()
                .withPrompts(Map.of(systemPrompt.traceKey(), systemPrompt.version())));
        return Flux.just(traceToken).concatWith(statusSink.asFlux().mergeWith(textTokens))
                .concatWith(Mono.fromSupplier(() -> new TurnTraceToken(
                        TurnTrace.empty()
                                .withToolCalls(toolCallRecorder.calls())
                                .withPrompts(Map.of(systemPrompt.traceKey(), systemPrompt.version()))
                                .withUsage(new TurnUsage(usageTracker.agentCalls(), List.of())))));
    }

    private Prompt attachmentSystemPrompt(Question question) {
        // Prompt sans RAG : seul le tool de pièces jointes est exposé au LLM.
        return promptRegistry.get(PromptId.ATTACHMENT_SYSTEM, question.language());
    }
}
