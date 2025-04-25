package zas.admin.zec.backend.agent;

public enum AgentType {
    RAG_AGENT, CHAT_AGENT, PENSION_AGENT, AI_AGENT;

    public static AgentType fromString(String type) {
        for (AgentType agentType : AgentType.values()) {
            if (agentType.name().equalsIgnoreCase(type)) {
                return agentType;
            }
        }
        return RAG_AGENT;
    }
}
