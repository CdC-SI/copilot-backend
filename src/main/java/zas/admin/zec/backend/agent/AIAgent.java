package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.agent.tools.ai.AITools;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.util.List;

@Service
public class AIAgent implements Agent {

    private final ChatClient client;

    public AIAgent(ChatModel model) {
        this.client = ChatClient.create(model);
    }

    @Override
    public String getName() {
        return "AI_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.AI_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        System.out.println("--------------------------------------------------\n" + question + "\n--------------------------------------------------");

        Flux<TextToken> clientTokens = client
                .prompt()
                .user(question.query())
                .tools(new AITools())
                // .tools(AITools.ircCallback)
                .stream()
                .content()
                .map(TextToken::new);

        return Flux.concat(clientTokens);
    }
}
