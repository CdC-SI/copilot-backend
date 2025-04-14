package zas.admin.zec.backend.rag;

import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.token.Token;

import java.util.List;

public interface IRAGService {
    Flux<Token> streamAnswer(Question question, List<Message> conversationHistory);
}
