package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@ConfigurationProperties(prefix = "faq.search")
@EnableConfigurationProperties({TrigramMatchingProperties.class, SemanticMatchingProperties.class})
public record FAQSearchProperties(
        @NotNull Integer minResultBeforeSemanticSearch,
        @NotNull TrigramMatchingProperties trigramMatching,
        @NotNull SemanticMatchingProperties semanticMatching) {}

