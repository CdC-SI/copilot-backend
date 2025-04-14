package zas.admin.zec.backend.agent;

public enum AgentType {
    RAG, CHAT, PENSION;

    public static AgentType fromString(String type) {
        for (AgentType agentType : AgentType.values()) {
            if (agentType.name().equalsIgnoreCase(type)) {
                return agentType;
            }
        }
        return RAG;
    }
}
