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

    /**
     * Constructs an instance of IIAgent.
     *
     * @param model the ChatModel instance representing the language model used for processing user input
     * @param holder the ConversationMetaDataHolder instance for managing metadata related to conversations
     */
    public IIAgent(ChatModel model, ConversationMetaDataHolder holder) {
        this.model = model;
        this.client = ChatClient.create(model);
        this.holder = holder;
    }

    /**
     * Retrieves the name of this agent.
     *
     * @return the name of the agent as a String
     */
    @Override
    public String getName() {
        return "AI_AGENT";
    }

    /**
     * Retrieves the type of this agent.
     *
     * @return the type of the agent as an instance of AgentType
     */
    @Override
    public AgentType getType() {
        return AgentType.II_AGENT;
    }

    /**
     * Processes the given question by analyzing the input, utilizing the conversation history,
     * applying tools and advisors, and streaming the resulting tokens.
     *
     * @param question the Question object containing the details of the query to be processed
     * @param userId the identifier for the user submitting the query
     * @param conversationHistory a list of previous messages in the conversation, used for context
     * @return a Flux stream of Token objects, representing the processed response to the question
     */
    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
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

    /**
     * Converts a ChatResponse object into a corresponding Token instance. The conversion logic
     * determines the type of token based on the content and metadata of the ChatResponse.
     * If no relevant data is found, a default empty TextToken is returned.
     *
     * @param r the ChatResponse object to be converted into a Token
     * @return a Token object reflecting the content or metadata of the ChatResponse
     */
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
