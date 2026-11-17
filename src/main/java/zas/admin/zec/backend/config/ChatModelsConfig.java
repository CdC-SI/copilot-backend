package zas.admin.zec.backend.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.util.UriComponentsBuilder;
import zas.admin.zec.backend.config.properties.InternalChatModelProperties;
import zas.admin.zec.backend.config.properties.LlmResilienceProperties;
import zas.admin.zec.backend.config.properties.PublicChatModelProperties;

@Configuration
@EnableConfigurationProperties({PublicChatModelProperties.class, InternalChatModelProperties.class,
        LlmResilienceProperties.class})
public class ChatModelsConfig {

    static final String INTERNAL_CHAT_CIRCUIT_BREAKER = "internal-chat";

    private final InternalChatModelProperties internalChatModelProperties;
    private final LlmResilienceProperties resilienceProperties;
    private final MeterRegistry meterRegistry;

    public ChatModelsConfig(InternalChatModelProperties internalChatModelProperties,
                            LlmResilienceProperties resilienceProperties,
                            ObjectProvider<MeterRegistry> meterRegistry) {
        this.internalChatModelProperties = internalChatModelProperties;
        this.resilienceProperties = resilienceProperties;
        this.meterRegistry = meterRegistry.getIfUnique(SimpleMeterRegistry::new);
    }

    /**
     * Modèle de chat interne, enveloppé par {@link ResilientChatModel} (B4) : nouvelles tentatives avant le premier
     * token, circuit breaker et bascule vers le modèle de repli s'il est configuré. Les nouvelles tentatives du client
     * OpenAI sont désactivées pour ne pas les cumuler.
     */
    @Bean(name = "internalChatModel")
    public ChatModel internalChatModel() {
        ChatModel primary = chatModel(internalChatModelProperties.apiKey(), internalChatModelProperties.chatBaseUrl(),
                internalChatModelProperties.chatModel(), "low");
        var fallbackProperties = resilienceProperties.fallback();
        ChatModel fallback = fallbackProperties.enabled()
                ? chatModel(fallbackProperties.apiKey(), fallbackProperties.chatBaseUrl(),
                        fallbackProperties.chatModel(), blankToNull(fallbackProperties.reasoningEffort()))
                : null;

        var breaker = resilienceProperties.circuitBreaker();
        var circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(breaker.failureRateThreshold())
                .slidingWindowSize(breaker.slidingWindowSize())
                .minimumNumberOfCalls(breaker.minimumNumberOfCalls())
                .waitDurationInOpenState(breaker.waitDurationInOpenState())
                .recordException(ResilientChatModel::isTransient)
                .build());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakers).bindTo(meterRegistry);

        return new ResilientChatModel(primary, fallback, fallbackProperties.chatModel(),
                fallbackProperties.reasoningEffort(), circuitBreakers.circuitBreaker(INTERNAL_CHAT_CIRCUIT_BREAKER),
                resilienceProperties.maxAttempts(), resilienceProperties.initialBackoff(),
                resilienceProperties.maxBackoff(), resilienceProperties.firstTokenTimeout(), meterRegistry);
    }

    private OpenAiChatModel chatModel(String apiKey, String baseUrl, String model, String reasoningEffort) {
        var options = OpenAiChatOptions.builder()
                .apiKey(apiKey)
                .baseUrl(apiBaseUrl(baseUrl))
                .model(model)
                .temperature(0.0)
                .maxTokens(16384)
                .reasoningEffort(reasoningEffort)
                // Usage (tokens) dans le dernier chunk de chaque appel streamé : persisté par tour (chat_history.usage).
                .streamUsage(true)
                .timeout(resilienceProperties.requestTimeout())
                .maxRetries(0)
                .build();

        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
