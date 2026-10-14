package zas.admin.zec.backend.tools.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.LocalConfig;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Socle des tests d'intégration des {@link DocumentLookup} : PostgreSQL Testcontainers migré par Flyway (V17–V19),
 * données synthétiques insérées par chaque test dans une transaction annulée. Configuration commune afin que
 * les sous-classes partagent le même contexte Spring.
 */
@SpringBootTest(properties = {
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
@Transactional
abstract class AbstractDocumentLookupIT {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "internalEmbeddingModel")
    private OpenAiEmbeddingModel embeddingModel;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws NoSuchAlgorithmException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        registry.add("zas.security.blue-token.public-key", () -> key);
    }

    protected static MetadataFilterSpec eq(String key, String value) {
        return new MetadataFilterSpec(key, FilterOp.EQ, value, null);
    }

    protected static MetadataFilterSpec op(String key, FilterOp op, String value) {
        return new MetadataFilterSpec(key, op, value, null);
    }

    protected static MetadataFilterSpec in(String key, String... values) {
        return new MetadataFilterSpec(key, FilterOp.IN, null, List.of(values));
    }

    protected static DocumentLookup.LookupRequest request(List<MetadataFilterSpec> filters, String userId,
                                                          boolean internalAccess, String language) {
        return new DocumentLookup.LookupRequest(filters, 5, userId, internalAccess, language, true);
    }

    protected static List<String> ids(DocumentLookup.LookupResult result) {
        return result.documents().stream().map(Document::getId).toList();
    }

    protected static String json(Map<String, ?> value) {
        return JSON.writeValueAsString(value);
    }
}
