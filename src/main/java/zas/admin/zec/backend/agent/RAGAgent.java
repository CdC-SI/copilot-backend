package zas.admin.zec.backend.agent;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.RAGService;
import zas.admin.zec.backend.rag.token.Token;

import java.util.List;

@Service
public class RAGAgent implements Agent{

    private final RAGService ragService;

    public RAGAgent(RAGService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String getName() {
        return "RAG_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.RAG;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        return ragService.streamAnswer(question, conversationHistory);
    }
}
