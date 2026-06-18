package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;
import zas.admin.zec.backend.tools.RAGTool;
import zas.admin.zec.backend.tools.ToolContextKeys;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service de conversation "agentique" : un unique {@link ChatClient} auquel sont attribués le tool
 * {@link RAGTool} (recherche documentaire) et {@link ConversationAttachmentTool} (pièces jointes).
 * Le LLM décide lui-même d'invoquer ces tools via tool-calling.
 *
 * <p>Les données non fournies par le LLM (userId, langue, workspace, conversationId) transitent par
 * le {@code ToolContext}. Les documents éventuellement récupérés par le tool sont collectés via une
 * liste partagée déposée dans le {@code ToolContext}, puis convertis en {@link SourceToken} une fois
 * la réponse générée.</p>
 */
@Slf4j
@Service
public class RAGChatService {

    private static final String META_TITLE = "title";
    private static final String META_STATE = "state";
    private static final String META_URL = "url";
    private static final String META_PAGE_NUM = "page_num";
    private static final String META_SUBSECTION = "subsection";

    private final ChatClient internalChatClient;
    private final RAGTool ragTool;
    private final ConversationAttachmentTool attachmentTool;

    public RAGChatService(@Qualifier("internalChatModel") ChatModel internalChatModel,
                          RAGTool ragTool,
                          ConversationAttachmentTool attachmentTool) {
        this.internalChatClient = ChatClient.create(internalChatModel);
        this.ragTool = ragTool;
        this.attachmentTool = attachmentTool;
    }

    public Flux<Token> answer(Question question, String userId, List<Message> conversationHistory) {
        // Liste partagée (thread-safe) que le tool alimentera lors d'un éventuel appel.
        List<Document> retrievedDocuments = new CopyOnWriteArrayList<>();

        // Sink partagé (thread-safe) dans lequel les tools émettent des StatusToken
        // avant/pendant leur traitement, pour notifier le frontend en temps réel.
        Sinks.Many<Token> statusSink = Sinks.many().unicast().onBackpressureBuffer();

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(ToolContextKeys.CTX_USER_ID, userId);
        toolContext.put(ToolContextKeys.CTX_LANGUAGE, question.language());
        toolContext.put(ToolContextKeys.CTX_WORKSPACE, question.workspace());
        toolContext.put(ToolContextKeys.CTX_CONVERSATION_ID, question.conversationId());
        toolContext.put(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, retrievedDocuments);
        toolContext.put(ToolContextKeys.CTX_STATUS_SINK, statusSink);

        Flux<Token> textTokens = internalChatClient
                .prompt()
                .system(agenticSystemPrompt(question))
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .tools(ragTool, attachmentTool)
                .toolContext(toolContext)
                .user(question.query())
                .stream()
                .chatResponse()
                .flatMap(this::toTextToken)
                .doFinally(signal -> statusSink.tryEmitComplete());

        // Les sources ne sont connues qu'après la génération (si le tool a été appelé).
        Flux<Token> sourceTokens = Flux.defer(() -> toSourceTokens(retrievedDocuments));

        // statusSink.asFlux() émet les StatusToken produits pendant le tool-calling,
        // avant et pendant que textTokens streame la réponse du LLM.
        return statusSink.asFlux().mergeWith(textTokens).concatWith(sourceTokens);
    }

    private String agenticSystemPrompt(Question question) {
        // Prompt non orienté RAG : le LLM décide lui-même d'appeler le tool de recherche
        // documentaire, et répond directement aux demandes situationnelles (résumé, traduction...).
        return RAGPrompts.getAgenticSystemPrompt(question.language())
                .formatted(question.responseFormat());
    }

    private Flux<Token> toTextToken(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return Flux.just(new TextToken(""));
        }
        var text = response.getResult().getOutput().getText();
        return Flux.just(new TextToken(text != null ? text : ""));
    }

    private Flux<Token> toSourceTokens(List<Document> documents) {
        if (documents.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(documents)
                .map(this::toSourceToken);
    }

    private SourceToken toSourceToken(Document document) {
        var meta = document.getMetadata();
        var url = (String) meta.get(META_URL);
        if (url != null && !url.isBlank()) {
            return SourceToken.fromURLWithDetails(
                    document.getId(),
                    url,
                    (String) meta.get(META_PAGE_NUM),
                    (String) meta.get(META_SUBSECTION),
                    (String) meta.get(META_STATE)
            );
        }
        return SourceToken.fromFileWithDetails(
                document.getId(),
                (String) meta.get(META_TITLE),
                (String) meta.get(META_PAGE_NUM),
                (String) meta.get(META_SUBSECTION),
                (String) meta.get(META_STATE)
        );
    }

    private org.springframework.ai.chat.messages.Message convertToMessage(Message message) {
        return "USER".equals(message.role())
                ? new UserMessage(message.message())
                : new AssistantMessage(message.message());
    }
}


