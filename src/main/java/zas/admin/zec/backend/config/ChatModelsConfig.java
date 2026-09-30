package zas.admin.zec.backend.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.util.UriComponentsBuilder;
import zas.admin.zec.backend.config.properties.InternalChatModelProperties;
import zas.admin.zec.backend.config.properties.PublicChatModelProperties;

@Configuration
@EnableConfigurationProperties({PublicChatModelProperties.class, InternalChatModelProperties.class})
public class ChatModelsConfig {

    private final InternalChatModelProperties internalChatModelProperties;

    public ChatModelsConfig(InternalChatModelProperties internalChatModelProperties) {
        this.internalChatModelProperties = internalChatModelProperties;
    }

    @Bean(name = "internalChatModel")
    public ChatModel internalChatModel() {
        var internalChatOptions = OpenAiChatOptions.builder()
                .apiKey(internalChatModelProperties.apiKey())
                .baseUrl(apiBaseUrl(internalChatModelProperties.chatBaseUrl()))
                .model(internalChatModelProperties.chatModel())
                .temperature(0.0)
                .maxTokens(16384)
                .reasoningEffort("low")
                .build();

        return OpenAiChatModel.builder()
                .options(internalChatOptions)
                .build();
    }

    @Bean(name = "visionModel")
    public ChatModel visionModel() {
        var options = OpenAiChatOptions.builder()
                .apiKey(internalChatModelProperties.apiKey())
                .baseUrl(apiBaseUrl(internalChatModelProperties.visionBaseUrl()))
                .model(internalChatModelProperties.visionModel())
                .temperature(0.0)
                .maxTokens(15360)
                .build();

        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }

    @Primary
    @Bean(name = "internalEmbeddingModel")
    public OpenAiEmbeddingModel internalEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .metadataMode(MetadataMode.EMBED)
                .options(OpenAiEmbeddingOptions.builder()
                        .apiKey(internalChatModelProperties.apiKey())
                        .baseUrl(apiBaseUrl(internalChatModelProperties.embeddingBaseUrl()))
                        .model(internalChatModelProperties.embeddingModel())
                        .build())
                .build();
    }

    private static String apiBaseUrl(String baseUrl) {
        // OpenAiApi appended /v1; the SDK expects it in the base URL.
        return UriComponentsBuilder.fromUriString(baseUrl).pathSegment("v1").toUriString();
    }
}
