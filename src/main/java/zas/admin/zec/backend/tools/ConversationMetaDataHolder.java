package zas.admin.zec.backend.tools;

import org.springframework.stereotype.Component;
import zas.admin.zec.backend.agent.AgentType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public final class ConversationMetaDataHolder {

    private static final String CURRENT_AGENT_IN_USE = "currentAgentInUse";
    private final Map<String, Map<String, Object>> metaDataByConversationId = new HashMap<>();

    private ConversationMetaDataHolder() {}

    public Optional<AgentType> getCurrentAgentInUse(String conversationId) {
        return Optional.ofNullable(metaDataByConversationId.get(conversationId))
                .map(metaData -> (AgentType) metaData.get(CURRENT_AGENT_IN_USE));
    }

    public void setCurrentAgentInUse(String conversationId, AgentType agent) {
        metaDataByConversationId
                .computeIfAbsent(conversationId, k -> new HashMap<>())
                .put(CURRENT_AGENT_IN_USE, agent);
    }

    public Optional<String> getStep(String conversationId) {
        return Optional.ofNullable(metaDataByConversationId.get(conversationId))
                .map(metaData -> (String) metaData.get("step"));
    }

    public void setStep(String conversationId, String step) {
        metaDataByConversationId
                .computeIfAbsent(conversationId, k -> new HashMap<>())
                .put("step", step);
    }

    public void clearMetaData(String conversationId) {
        metaDataByConversationId.remove(conversationId);
    }
}
