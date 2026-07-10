package zas.admin.zec.backend.actions.converse;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.ToolContextKeys;

import java.util.HashMap;
import java.util.Map;

/**
 * Base commune aux implémentations de {@link ChatService}, regroupant les helpers partagés :
 * conversion de l'historique en messages Spring AI, extraction du texte d'un {@link ChatResponse},
 * et construction du socle du {@code ToolContext} (données non fournies par le LLM).
 */
abstract class AbstractChatService implements ChatService {

    /**
     * Construit le socle du {@code ToolContext} commun à toutes les stratégies : identité de
     * l'utilisateur, langue, conversation courante et sink de statut partagé. Les implémentations
     * peuvent y ajouter leurs propres clés (documents récupérés, workspace résolu, etc.).
     */
    protected Map<String, Object> baseToolContext(Question question, String userId, Sinks.Many<Token> statusSink) {
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(ToolContextKeys.CTX_USER_ID, userId);
        toolContext.put(ToolContextKeys.CTX_LANGUAGE, question.language());
        toolContext.put(ToolContextKeys.CTX_CONVERSATION_ID, question.conversationId());
        toolContext.put(ToolContextKeys.CTX_STATUS_SINK, statusSink);
        return toolContext;
    }

    protected Flux<Token> toTextToken(ChatResponse response) {
        if (response == null) {
            return Flux.just(new TextToken(""));
        } else {
            response.getResult();
        }
        var text = response.getResult().getOutput().getText();
        return Flux.just(new TextToken(text != null ? text : ""));
    }

    protected org.springframework.ai.chat.messages.Message convertToMessage(Message message) {
        return "USER".equals(message.role())
                ? new UserMessage(message.message())
                : new AssistantMessage(message.message());
    }
}
