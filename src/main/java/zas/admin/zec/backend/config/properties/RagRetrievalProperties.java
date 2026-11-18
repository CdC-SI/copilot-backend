package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Moteur de recherche documentaire de {@code RAGTool} (T2) : {@code local} (pgvector, BM25 et reranker du backend)
 * ou {@code zia-rag} (service zia-rag, {@code POST /api/v1/retrieval/documents}).
 */
@ConfigurationProperties(prefix = "rag.retrieval")
public record RagRetrievalProperties(Provider provider, ZiaRag ziaRag) {

    public enum Provider { LOCAL, ZIA_RAG }

    public RagRetrievalProperties {
        if (provider == null) {
            provider = Provider.LOCAL;
        }
        if (ziaRag == null) {
            ziaRag = new ZiaRag(null, null, null);
        }
    }

    public boolean ziaRagEnabled() {
        return provider == Provider.ZIA_RAG;
    }

    /** Accès au service zia-rag ; {@code baseUrl} est obligatoire quand le provider est {@code zia-rag}. */
    public record ZiaRag(String baseUrl, Duration connectTimeout, Duration readTimeout) {

        public ZiaRag {
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(2);
            }
            if (readTimeout == null) {
                readTimeout = Duration.ofSeconds(20);
            }
        }
    }
}
