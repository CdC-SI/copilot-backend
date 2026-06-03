package zas.admin.zec.backend.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.converse.Question;

import java.util.Set;

import static zas.admin.zec.backend.agent.AgentType.RAG_AGENT;

@Slf4j
@Component
public class AgentFactory {

    private final Set<Agent> agents;

    @Autowired
    public AgentFactory(Set<Agent> agents) {
        this.agents = agents;
    }

    public Agent selectAppropriateAgent(Question question) {
        var agentType = RAG_AGENT;
        return agents.stream()
                .filter(agent -> agent.getType() == agentType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No agent found for type: " + agentType));
    }
}
