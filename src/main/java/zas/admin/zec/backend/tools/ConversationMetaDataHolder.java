package zas.admin.zec.backend.tools;

import org.springframework.stereotype.Component;
import zas.admin.zec.backend.agent.AgentType;
import zas.admin.zec.backend.agent.tools.ii.IITools;

import java.util.HashMap;
import java.util.List;
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

    public Optional<List<IITools.Qa>> getAnsweredQuestions(String conversationId) {
        return Optional.ofNullable(metaDataByConversationId.get(conversationId))
                .map(metaData -> (List<IITools.Qa>) metaData.get("answeredQuestion"));
    }

    public void setAnsweredQuestions(String conversationId, List<IITools.Qa> question) {
        metaDataByConversationId
                .computeIfAbsent(conversationId, k -> new HashMap<>())
                .put("answeredQuestion", question);
    }

    public Optional<String> getEtape(String conversationId) {
        return Optional.ofNullable(metaDataByConversationId.get(conversationId))
                .map(metaData -> (String) metaData.get("etape"));
    }

    public void setEtape(String conversationId, String etape) {
        metaDataByConversationId
                .computeIfAbsent(conversationId, k -> new HashMap<>())
                .put("etape", etape);
    }

    public void clearMetaData(String conversationId) {
        metaDataByConversationId.remove(conversationId);
    }
}
