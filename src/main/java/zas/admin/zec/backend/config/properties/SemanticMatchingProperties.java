package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "faq.search.semantic-matching")
public record SemanticMatchingProperties(@NotNull Integer limit, @NotNull String metric) {
}
