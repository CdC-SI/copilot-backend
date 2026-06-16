package zas.admin.zec.backend.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
        var localAiApi = OpenAiApi.builder()
                .apiKey(internalChatModelProperties.apiKey())
                .baseUrl(internalChatModelProperties.chatBaseUrl())
                .build();

        var internalChatOptions = OpenAiChatOptions.builder()
                .model(internalChatModelProperties.chatModel())
                .temperature(0.0)
                .maxTokens(16384)
                .reasoningEffort("low")
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(localAiApi)
                .defaultOptions(internalChatOptions)
                .build();
    }

    @Bean(name = "visionModel")
    public ChatModel visionModel() {
        var api = OpenAiApi.builder()
                .apiKey(internalChatModelProperties.apiKey())
                .baseUrl(internalChatModelProperties.visionBaseUrl())
                .build();

        var options = OpenAiChatOptions.builder()
                .model(internalChatModelProperties.visionModel())
                .temperature(0.0)
                .maxTokens(15360)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    @Primary
    @Bean(name = "internalEmbeddingModel")
    public OpenAiEmbeddingModel internalEmbeddingModel() {
        var openAiApi = OpenAiApi.builder()
                .apiKey(internalChatModelProperties.apiKey())
                .baseUrl(internalChatModelProperties.embeddingBaseUrl())
                .build();

        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(internalChatModelProperties.embeddingModel())
                        //.dimensions(internalChatModelProperties.embeddingDimensions())
                        .build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }
}
