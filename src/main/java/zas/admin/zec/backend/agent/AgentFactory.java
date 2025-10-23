package zas.admin.zec.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.util.Optional;
import java.util.Set;

import static zas.admin.zec.backend.agent.AgentType.RAG_AGENT;

@Slf4j
@Component
public class AgentFactory {

    private final ObjectMapper objectMapper;
    private final Set<Agent> agents;
    private final ChatClient chatClient;
    private final ConversationMetaDataHolder conversationMetaDataHolder;

    @Autowired
    public AgentFactory(Set<Agent> agents, @Qualifier("internalChatModel") ChatModel chatModel, ConversationMetaDataHolder conversationMetaDataHolder) {
        this.agents = agents;
        this.chatClient = ChatClient.create(chatModel);
        this.conversationMetaDataHolder = conversationMetaDataHolder;
        objectMapper = new ObjectMapper();
    }

    public record AgentHandoff(String agent) {}
    public Agent selectAppropriateAgent(Question question) {
        var agentType = selectAgentType(question);
        return agents.stream()
                .filter(agent -> agent.getType() == agentType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No agent found for type: " + agentType));
    }

    private AgentType selectAgentType(Question question) {
        return conversationMetaDataHolder.getCurrentAgentInUse(question.conversationId())
                .or(() -> detectDevisAttachment(question))
                .orElseGet(() -> inferAgentType(question));
    }

    private Optional<AgentType> detectDevisAttachment(Question question) {
        var attachments = question.attachments();
        if (attachments == null || attachments.isEmpty()) return Optional.empty();

        boolean allFilesAreDevis = attachments
                .stream()
                .allMatch(file ->
                        file.getOriginalFilename() != null && file.getOriginalFilename().contains("devis"));

        return allFilesAreDevis
                ? Optional.of(AgentType.II_TARIFF_AGENT)
                : Optional.empty();
    }

    private AgentType inferAgentType(Question question) {
        var systemPrompt = AgentPrompts.getAgentSelectionPrompt(question.language())
                .formatted(question.query(), "");

        String jsonSchema = JsonSchemaGenerator.generateForType(AgentHandoff.class);

        var options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                .build();

        var inferredAgent = chatClient
                .prompt()
                .system(systemPrompt)
                .user(question.query())
                .options(options)
                .call()
                .content();

        return convertJsonToAgentType(inferredAgent);
    }

    private AgentType convertJsonToAgentType(String inferredAgent) {
        AgentHandoff agentHandoff;
        try {
            agentHandoff = objectMapper.readValue(inferredAgent, AgentHandoff.class);
            return AgentType.fromString(agentHandoff.agent());
        } catch (Exception e) {
            log.error("Failed to parse agent inference response: {}", inferredAgent, e);
        }

        return RAG_AGENT;
    }
}
