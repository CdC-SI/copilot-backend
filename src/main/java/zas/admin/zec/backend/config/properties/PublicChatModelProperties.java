package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.chat.public")
public record PublicChatModelProperties(String embeddingModel, String apiKey, String chatModel) {
}
