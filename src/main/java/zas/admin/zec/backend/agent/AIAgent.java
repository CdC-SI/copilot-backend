package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.agent.tools.ai.AITools;
import zas.admin.zec.backend.agent.tools.ai.IncomeCalculation;
import zas.admin.zec.backend.agent.tools.pension.PensionTools;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

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


        Flux<TextToken> clientTokens = client
                .prompt()
                .user(question.query())
                .tools(AITools.ircCallback)
                .stream()
                .content()
                .map(TextToken::new);

        return Flux.concat(clientTokens);
    }
}
