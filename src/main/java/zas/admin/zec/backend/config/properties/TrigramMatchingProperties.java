package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "faq.search.trigram-matching")
public record TrigramMatchingProperties(@NotNull Integer limit, @NotNull Double threshold) {}
