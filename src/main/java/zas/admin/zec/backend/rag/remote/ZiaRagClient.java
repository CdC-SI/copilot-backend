package zas.admin.zec.backend.rag.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zas.admin.zec.backend.config.properties.ZiaRagProperties;
import zas.admin.zec.backend.observability.CorrelationId;

import java.util.List;
import java.util.Map;

/**
 * Client HTTP du service zia-rag ({@code POST /api/v1/retrieval/documents}, T2). Appel direct (sans proxy), avec
 * timeouts, span client et en-tête {@code X-Correlation-Id} ; le jeton Blue de l'utilisateur est transmis tel quel.
 */
@Component
public class ZiaRagClient {

    static final String DOCUMENTS_PATH = "/api/v1/retrieval/documents";
    static final String TOKEN_HEADER = "Blue";

    private final RestClient restClient;

    @Autowired
    public ZiaRagClient(ZiaRagProperties properties, ObjectProvider<ObservationRegistry> observationRegistry) {
        this(configure(RestClient.builder(), properties,
                observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)).build());
    }

    ZiaRagClient(RestClient restClient) {
        this.restClient = restClient;
    }

    static RestClient.Builder configure(RestClient.Builder builder, ZiaRagProperties ziaRag,
                                        ObservationRegistry observationRegistry) {
        if (ziaRag.baseUrl() == null || ziaRag.baseUrl().isBlank()) {
            throw new IllegalStateException("rag.zia-rag.base-url (ZIA_RAG_BASE_URL) est requis");
        }
        var settings = HttpClientSettings.defaults().withTimeouts(ziaRag.connectTimeout(), ziaRag.readTimeout());
        return builder
                .baseUrl(ziaRag.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .requestInterceptor(CorrelationId.restClientInterceptor())
                .observationRegistry(observationRegistry);
    }

    /** Documents les plus pertinents pour la requête ; {@code bearer} : jeton Blue de l'utilisateur ({@code Bearer <jwt>}). */
    public List<Document> retrieve(Request request, String bearer) {
        List<RetrievedDocument> documents = restClient.post()
                .uri(DOCUMENTS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (bearer != null && !bearer.isBlank()) {
                        headers.set(TOKEN_HEADER, bearer);
                    }
                })
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return documents == null ? List.of() : documents.stream().map(RetrievedDocument::toDocument).toList();
    }

    /** Corps de la requête zia-rag ({@code RetrievalRequest}) ; {@code userId} donne accès aux documents privés. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(String question, Config config, String userId) {}

    /**
     * Configuration de la recherche zia-rag ({@code RetrievalConfig}) : seul le filtre sur les métadonnées est
     * transmis, les autres réglages sont les valeurs par défaut de zia-rag.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Config(Map<String, List<String>> metadataFilters) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RetrievedDocument(String id, String text, Map<String, Object> metadata, Double score) {

        Document toDocument() {
            var builder = Document.builder()
                    .text(text == null ? "" : text)
                    .metadata(metadata == null ? Map.of() : metadata)
                    .score(score);
            if (id != null && !id.isBlank()) {
                builder.id(id);
            }
            return builder.build();
        }
    }
}
