package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.chat.internal")
public record InternalChatModelProperties(
        String apiKey,
        String chatModel,
        String chatBaseUrl,
        String embeddingModel,
        String embeddingBaseUrl,
        Integer embeddingDimensions) {}
