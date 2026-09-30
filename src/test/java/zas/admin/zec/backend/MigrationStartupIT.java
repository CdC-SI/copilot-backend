package zas.admin.zec.backend;

import com.github.gavlyukovskiy.boot.jdbc.decorator.DecoratedDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password=",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.ai.chat.internal.api-key=test-key",
        "spring.ai.chat.internal.chat-model=test-chat",
        "spring.ai.chat.internal.chat-base-url=http://127.0.0.1:1",
        "spring.ai.chat.internal.vision-model=test-vision",
        "spring.ai.chat.internal.vision-base-url=http://127.0.0.1:1",
        "spring.ai.chat.internal.embedding-model=test-embedding",
        "spring.ai.chat.internal.embedding-base-url=http://127.0.0.1:1",
        "spring.ai.chat.internal.reranker-model=test-reranker",
        "spring.ai.chat.internal.reranker-base-url=http://127.0.0.1:1",
        "embedding-service.base-url=http://127.0.0.1:1",
        "spring.activemq.broker-url=tcp://127.0.0.1:1",
        "spring.activemq.user=test",
        "spring.activemq.password=test",
        "management.health.jms.enabled=false",
        "management.health.mail.enabled=false",
        "proxy.enabled=false",
        "ai.agent.ii-data-folder=.",
        "nuxeo.url=http://127.0.0.1:1",
        "nuxeo.username=test",
        "nuxeo.password=test",
        "logging.level.org.springframework=INFO"
})
@Import(LocalConfig.class)
class MigrationStartupIT {

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private VectorStore vectorStore;

    @MockitoBean(name = "internalEmbeddingModel")
    private OpenAiEmbeddingModel embeddingModel;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws NoSuchAlgorithmException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        registry.add("zas.security.blue-token.public-key", () -> key);
    }

    @Test
    void applicationStartsWithMigratedDatabaseAndPublicHttpContracts() throws Exception {
        assertInstanceOf(DecoratedDataSource.class, jdbcTemplate.getDataSource());
        assertTrue(jdbcTemplate.queryForObject(
                "select count(*) > 0 from flyway_schema_history where success", Boolean.class));
        assertNotNull(jdbcTemplate.queryForObject("select count(*) from vector_store", Long.class));

        try (var client = HttpClient.newHttpClient()) {
            var health = client.send(request("/actuator/health").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode(), health.body());
            assertEquals("UP", mapper.readTree(health.body()).path("status").asText());

            var api = client.send(request("/api/public/v1/chat")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"input\":\"hello\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, api.statusCode());

            var docs = client.send(request("/api/public/v3/api-docs").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, docs.statusCode(), docs.body());
            assertTrue(mapper.readTree(docs.body()).has("openapi"));
        }
    }

    @Test
    @Transactional
    void hibernateAndPgvectorShareTheExistingMetadataAndVectorSchema() {
        float[] embedding = new float[1024];
        embedding[0] = 1.0f;
        when(embeddingModel.embed("document")).thenReturn(embedding);
        var document = new DocumentEntity();
        document.setContent("test document");
        document.setMetadata(Map.of("source", "migration-test", "language", "fr"));
        document.setEmbedding(embedding);
        documents.saveAndFlush(document);

        var results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("document").filterExpression("source == 'migration-test'").topK(1).build());

        assertEquals(1, results.size());
        assertEquals(document.getId().toString(), results.getFirst().getId());
        assertEquals("fr", results.getFirst().getMetadata().get("language"));
        assertEquals("test document", results.getFirst().getText());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + environment.getRequiredProperty("local.server.port") + "/zia" + path))
                .timeout(Duration.ofSeconds(15));
    }
}
