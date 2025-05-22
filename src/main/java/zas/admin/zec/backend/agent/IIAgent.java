package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.agent.advisors.IIAdvisor;
import zas.admin.zec.backend.agent.tools.ii.IITools;
import zas.admin.zec.backend.rag.token.SuggestionToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.util.List;

@Service
public class IIAgent implements Agent {

    private final ChatClient client;
    private final ChatModel model;
    private final ConversationMetaDataHolder holder;

    public IIAgent(ChatModel model, ConversationMetaDataHolder holder) {
        this.model = model;
        this.client = ChatClient.create(model);
        this.holder = holder;
    }

    @Override
    public String getName() {
        return "AI_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.II_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        //holder.setCurrentAgentInUse(question.conversationId(), AgentType.II_AGENT);
        return client
                .prompt()
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .user(question.query())
                .tools(new IITools(holder, question.conversationId()))
                .advisors(new IIAdvisor(holder, question.conversationId(), model))
                .stream()
                .chatResponse()
                .map(this::convertToToken);
    }

    private Token convertToToken(ChatResponse r) {
        if (r.getResults() == null || r.getResults().isEmpty()) {
            return new TextToken("");
        }
        if (r.getResult().getMetadata().containsKey("suggestion")) {
            return new SuggestionToken(r.getResult().getMetadata().get("suggestion"));
        }
        if (r.getResult().getOutput() == null || r.getResult().getOutput().getText() == null) {
            return new TextToken("");
        }

        return new TextToken(r.getResult().getOutput().getText());
    }

    private org.springframework.ai.chat.messages.Message convertToMessage(Message message) {
        return "USER".equals(message.role())
            ? new UserMessage(message.message())
            : new AssistantMessage(message.message());
    }
}
