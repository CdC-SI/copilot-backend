package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;

import java.util.List;
import java.util.Map;

/**
 * Stratégie {@link ChatService} "sans RAG" : un {@link ChatClient} auquel seul le tool
 * {@link ConversationAttachmentTool} (pièces jointes) est attribué. Aucune recherche documentaire
 * n'est effectuée : le LLM se comporte comme un assistant généraliste (rédaction, relecture,
 * traduction, reformulation, résumé, exploitation des pièces jointes...) et répond à partir de ses
 * connaissances générales, sans source officielle. Il reste discret sur les sujets internes ou
 * relevant du 1er pilier des assurances sociales (cf. {@code ATTACHMENT_SYSTEM_PROMPT}).
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
    private final ConversationAttachmentTool attachmentTool;

    public AttachmentChatService(@Qualifier("internalChatModel") ChatModel internalChatModel,
                                  ConversationAttachmentTool attachmentTool) {
        this.internalChatClient = ChatClient.create(internalChatModel);
        this.attachmentTool = attachmentTool;
    }

    @Override
    public boolean supports(ConversationType conversationType) {
        return conversationType == ConversationType.NO_RAG;
    }

    @Override
    public Flux<Token> answer(Question question, String userId, List<Message> conversationHistory) {
        // Sink partagé (thread-safe) dans lequel le tool émet des StatusToken (ex. OCR)
        // avant/pendant son traitement, pour notifier le frontend en temps réel.
        Sinks.Many<Token> statusSink = Sinks.many().unicast().onBackpressureBuffer();

        Map<String, Object> toolContext = baseToolContext(question, userId, statusSink);

        Flux<Token> textTokens = internalChatClient
                .prompt()
                .system(attachmentSystemPrompt(question))
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .tools(attachmentTool)
                .toolContext(toolContext)
                .user(question.query())
                .stream()
                .chatResponse()
                .flatMap(this::toTextToken)
                .doFinally(signal -> statusSink.tryEmitComplete());

        // statusSink.asFlux() émet les StatusToken produits pendant le tool-calling,
        // avant et pendant que textTokens streame la réponse du LLM.
        return statusSink.asFlux().mergeWith(textTokens);
    }

    private String attachmentSystemPrompt(Question question) {
        // Prompt sans RAG : seul le tool de pièces jointes est exposé au LLM.
        return RAGPrompts.getAttachmentSystemPrompt(question.language())
                .formatted(question.responseFormat());
    }
}
