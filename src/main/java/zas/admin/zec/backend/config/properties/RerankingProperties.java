package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.reranking")
public record RerankingProperties(@NotNull String cohereApiKey,
                                  @NotNull Float scoreThreshold,
                                  @NotNull Integer truncatePromptTokens,
                                  @NotNull Integer topK,
                                  @NotNull Boolean enabled) { }
