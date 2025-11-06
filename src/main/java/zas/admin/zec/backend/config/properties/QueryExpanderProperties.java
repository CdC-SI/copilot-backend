package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieving.query-expander")
public record QueryExpanderProperties(
        @NotNull Boolean enabled,
        @NotNull Integer numberOfExpansions,
        @NotNull Boolean includeOriginal) {}
