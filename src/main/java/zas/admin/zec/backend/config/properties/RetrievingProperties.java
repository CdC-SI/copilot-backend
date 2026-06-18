package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieving")
@EnableConfigurationProperties({QueryCompresserProperties.class, QueryRewriterProperties.class, QueryExpanderProperties.class, Bm25Properties.class})
public record RetrievingProperties(
        @NotNull Integer topK,
        @NotNull QueryCompresserProperties queryCompresser,
        @NotNull QueryRewriterProperties queryRewriter,
        @NotNull QueryExpanderProperties queryExpander,
        @NotNull Bm25Properties bm25) {}

