package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.agent.tools.chat.TextTools;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.TranslationService;

import java.util.List;
import java.util.Map;

@Service
public class ChatAgent implements Agent {

    private final TranslationService translationService;
    private final ChatClient client;

    public ChatAgent(TranslationService translationService, ChatModel model) {
        this.translationService = translationService;
        this.client = ChatClient.create(model);
    }

    @Override
    public String getName() {
        return "CHAT_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.CHAT_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        return client
                .prompt()
                .user(question.query())
                .tools(new TextTools(translationService))
                .toolContext(Map.of(TextTools.HISTORY, conversationHistory))
                .stream()
                .content()
                .map(TextToken::new);
    }
}
