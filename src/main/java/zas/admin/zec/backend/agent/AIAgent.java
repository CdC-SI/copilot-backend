package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.agent.tools.ai.AITools;
// import zas.admin.zec.backend.agent.advisors.ai.AIAdvisor;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIAgent implements Agent {

    private final ChatClient client;
    private final ChatMemory chatMemory;

    public AIAgent(ChatModel model) {
        this.client = ChatClient.create(model);
        this.chatMemory = new InMemoryChatMemory(); // Initialize chat memory
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
        
        // Map<String, Object> adviseContext = new HashMap<>();
        // adviseContext.put("chat_memory", chatMemory); // Pass chat memory to the context
        
        // Problème: l'historique de conversation n'est pris en compte que lorsque l'agent est détecté ->
        // une réponse à une question posée par l'agent peut ne pas être interprétée comme n'écessitant l'usage de l'agent
        // -> prendre en compte l'historique pour déterminer si l'agent doit être utilisé ou non.

        Flux<TextToken> clientTokens = client
                .prompt()
                .user(question.query())
                .tools(new AITools())
                .advisors(new MessageChatMemoryAdvisor(chatMemory, question.conversationId(), 5)) // Use the MessageChatMemoryAdvisor
                // .advisors(new AIAdvisor()) // Use the updated AIAdvisor
                // .adviseContext(adviseContext)
                .stream()
                .content()
                .map(TextToken::new);

        return Flux.concat(clientTokens);
    }
}
