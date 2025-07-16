package zas.admin.zec.backend.tools;

import org.springframework.stereotype.Component;
import zas.admin.zec.backend.agent.AgentType;
import zas.admin.zec.backend.agent.tools.IIStep;

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

    public Optional<IIStep> getStep(String conversationId) {
        return Optional.ofNullable(metaDataByConversationId.get(conversationId))
                .map(metaData -> (IIStep) metaData.get("step"));
    }

    public void setStep(String conversationId, IIStep step) {
        metaDataByConversationId
                .computeIfAbsent(conversationId, k -> new HashMap<>())
                .put("step", step);
    }

    public String getDecision(String conversationId) {
        return (String) metaDataByConversationId.get(conversationId).get("decision");
    }

    public void setDecision(String conversationId, String decision) {
        metaDataByConversationId
                .computeIfAbsent(conversationId, k -> new HashMap<>())
                .put("decision", decision);
    }

    public String getCalculation(String conversationId) {
        return (String) metaDataByConversationId.get(conversationId).get("calculation");
    }

    public void setCalculation(String conversationId, String calculation) {
        metaDataByConversationId
                .computeIfAbsent(conversationId, k -> new HashMap<>())
                .put("calculation", calculation);
    }

    public void clearMetaData(String conversationId) {
        metaDataByConversationId.remove(conversationId);
    }
}
