package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieving.bm25")
public record Bm25Properties(
        @NotNull Boolean enabled,
        @NotNull Integer topK,
        @NotNull String textSearchConfig) {}
