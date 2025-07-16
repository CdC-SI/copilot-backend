package zas.admin.zec.backend.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import zas.admin.zec.backend.config.properties.InternalChatModelProperties;
import zas.admin.zec.backend.config.properties.PublicChatModelProperties;

@Configuration
@EnableConfigurationProperties({PublicChatModelProperties.class, InternalChatModelProperties.class})
public class ChatModelsConfig {

    private final PublicChatModelProperties publicChatModelProperties;
    private final InternalChatModelProperties internalChatModelProperties;

    public ChatModelsConfig(PublicChatModelProperties publicChatModelProperties, InternalChatModelProperties internalChatModelProperties) {
        this.publicChatModelProperties = publicChatModelProperties;
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
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(localAiApi)
                .defaultOptions(internalChatOptions)
                .build();
    }

    @Bean(name = "publicChatModel")
    public ChatModel publicChatModel(ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                     ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        var openAiApi = OpenAiApi.builder()
                .apiKey(publicChatModelProperties.apiKey())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .build();

        var openAiChatOptions = OpenAiChatOptions.builder()
                .model(publicChatModelProperties.chatModel())
                .temperature(0.7)
                .maxTokens(4096)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(openAiChatOptions)
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

    @Bean(name = "publicEmbeddingModel")
    public OpenAiEmbeddingModel publicEmbeddingModel(ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        var openAiApi = OpenAiApi.builder()
                .apiKey(publicChatModelProperties.apiKey())
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .build();

        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(publicChatModelProperties.embeddingModel())
                        .build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }
}
