package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Accès au service zia-rag ({@code POST /api/v1/retrieval/documents}), seul moteur de recherche documentaire de
 * {@code RAGTool}. {@code baseUrl} est obligatoire.
 */
@ConfigurationProperties(prefix = "rag.zia-rag")
public record ZiaRagProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public ZiaRagProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(20);
        }
    }
}
