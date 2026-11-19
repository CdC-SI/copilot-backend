package zas.admin.zec.backend.rag.remote;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import zas.admin.zec.backend.config.properties.ZiaRagProperties;
import zas.admin.zec.backend.observability.CorrelationId;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Contrat HTTP avec zia-rag : corps de requête, en-têtes Blue et de corrélation, lecture des documents. */
class ZiaRagClientTest {

    private static final ZiaRagProperties ZIA_RAG = new ZiaRagProperties("http://zia-rag", null, null);

    private MockRestServiceServer server;
    private ZiaRagClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = ZiaRagClient.configure(RestClient.builder(), ZIA_RAG, ObservationRegistry.NOOP);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ZiaRagClient(builder.build());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void postsTheRequestWithUserTokenAndCorrelationIdAndMapsDocuments() {
        MDC.put(CorrelationId.MDC_KEY, "corr-1");
        server.expect(requestTo("http://zia-rag/api/v1/retrieval/documents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Blue", "Bearer jwt"))
                .andExpect(header(CorrelationId.HEADER, "corr-1"))
                .andExpect(content().json("""
                        {"question": "Quelle cotisation ?", "userId": "u1",
                         "config": {"metadataFilters": {"source": ["afac"]}}}
                        """, true))
                .andRespond(withSuccess("""
                        [{"id": "d1", "text": "Texte", "metadata": {"source": "afac"}, "score": 0.8,
                          "media": null, "formattedContent": "ignoré"}]
                        """, MediaType.APPLICATION_JSON));

        var documents = client.retrieve(new ZiaRagClient.Request("Quelle cotisation ?",
                new ZiaRagClient.Config(Map.of("source", List.of("afac"))), "u1"), "Bearer jwt");

        server.verify();
        assertEquals(1, documents.size());
        assertEquals("d1", documents.getFirst().getId());
        assertEquals("Texte", documents.getFirst().getText());
        assertEquals("afac", documents.getFirst().getMetadata().get("source"));
        assertEquals(0.8, documents.getFirst().getScore());
    }

    @Test
    void omitsNullFieldsAndTokenWhenUnknown() {
        server.expect(requestTo("http://zia-rag/api/v1/retrieval/documents"))
                .andExpect(headerDoesNotExist("Blue"))
                .andExpect(content().json("""
                        {"question": "q"}
                        """, true))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(client.retrieve(new ZiaRagClient.Request("q", null, null), null).isEmpty());
        server.verify();
    }

    @Test
    void propagatesServerErrors() {
        server.expect(requestTo("http://zia-rag/api/v1/retrieval/documents"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var request = new ZiaRagClient.Request("q", null, null);
        assertThrows(HttpServerErrorException.class, () -> client.retrieve(request, null));
    }

    @Test
    void requiresBaseUrl() {
        assertThrows(IllegalStateException.class, () -> ZiaRagClient.configure(RestClient.builder(),
                new ZiaRagProperties(" ", null, null), ObservationRegistry.NOOP));
    }
}
