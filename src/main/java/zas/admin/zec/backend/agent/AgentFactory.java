package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.converse.Question;

import java.util.Set;

@Component
public class AgentFactory {

    private final Set<Agent> agents;
    private final ChatClient chatClient;

    @Autowired
    public AgentFactory(Set<Agent> agents, ChatModel chatModel) {
        this.agents = agents;
        this.chatClient = ChatClient.create(chatModel);
    }

    record AgentSelection(String agent) {}
    public Agent selectAppropriateAgent(Question question) {
        var systemPrompt = AgentPrompts.getAgentSelectionPrompt(question.language())
                .formatted(question.query(), "");

        var inferredAgent = chatClient
                .prompt()
                .system(systemPrompt)
                .user(question.query())
                .call()
                .entity(AgentSelection.class);

        var agentType = inferredAgent != null
                ? AgentType.fromString(inferredAgent.agent())
                : AgentType.RAG_AGENT;

        return agents.stream()
                .filter(agent -> agent.getType() == agentType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No agent found for type: " + agentType));
    }
}
