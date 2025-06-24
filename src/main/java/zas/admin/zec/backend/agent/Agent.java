package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.token.Token;

import java.util.List;

public interface Agent {
    String getName();
    AgentType getType();
    Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory);

    default org.springframework.ai.chat.messages.Message convertToMessage(Message message) {
        return "USER".equals(message.role())
                ? new UserMessage(message.message())
                : new AssistantMessage(message.message());
    }
}
