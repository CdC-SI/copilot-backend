package zas.admin.zec.backend.agent;

import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.token.Token;

import java.util.List;

public interface Agent {
    String getName();
    AgentType getType();
    Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory);
}
