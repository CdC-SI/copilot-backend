package zas.admin.zec.backend.tools;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.LocalConfig;
import zas.admin.zec.backend.actions.askfaq.FAQItemLight;
import zas.admin.zec.backend.actions.askfaq.FAQService;
import zas.admin.zec.backend.persistence.MetadataValues;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.QuestionRepository;
import zas.admin.zec.backend.rag.retriever.BM25DocumentRetriever;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Non-régression des métadonnées {@code Map<String, Object>} de {@link DocumentEntity} sur {@code vector_store} :
 * types JSON natifs (tableaux, nombres), filtres du {@link RAGTool} (vectoriel et BM25), requêtes natives
 * de {@link DocumentRepository}, FAQ et lecture tolérante via {@link MetadataValues}.
 * Chaque test s'exécute dans une transaction annulée.
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
class DocumentMetadataIT {

    private static final float[] EMBEDDING = new float[1024];

    static {
        EMBEDDING[0] = 1.0f;
    }

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FAQService faqService;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean(name = "internalEmbeddingModel")
    private OpenAiEmbeddingModel embeddingModel;

    private final FilterExpressionBuilder b = new FilterExpressionBuilder();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws NoSuchAlgorithmException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        registry.add("zas.security.blue-token.public-key", () -> key);
    }

    @BeforeEach
    void mockEmbedding() {
        when(embeddingModel.embed(anyString())).thenReturn(EMBEDDING);
    }

    @Test
    void entityRoundTripKeepsNativeJsonTypes() {
        var id = save("A", Map.of("tags", List.of("AVS", "AI"), "page_number", 3, "title", "T")).getId();
        entityManager.flush();
        entityManager.clear();

        var metadata = documentRepository.findById(id).orElseThrow().getMetadata();

        assertEquals(List.of("AVS", "AI"), metadata.get("tags"));
        assertEquals(3, metadata.get("page_number"));
        assertEquals("T", metadata.get("title"));
    }

    @Test
    void vectorStoreReadsArraysAndFiltersOnContains() {
        save("A", Map.of("tags", List.of("AVS", "AI"), "page_number", 3));
        save("B", Map.of("tags", List.of("AVS-AI")));
        save("C", Map.of("tags", "AVS"));
        documentRepository.flush();

        var docs = vectorStore.similaritySearch(request().filterExpression(b.eq("tags", "AVS").build()).build());
        assertEquals(Set.of("A", "C"), labels(docs));

        var a = docs.stream().filter(d -> label(d).equals("A")).findFirst().orElseThrow();
        assertEquals(List.of("AVS", "AI"), a.getMetadata().get("tags"));
        assertEquals("AVS,AI", MetadataValues.getString(a.getMetadata(), "tags"));
        assertEquals("3", MetadataValues.getString(a.getMetadata(), "page_number"));
    }

    @Test
    void ragToolFilterExcludesInternalDocumentsForScalarAndArrayOrganizations() {
        persistRagToolFixtures();

        assertEquals(Set.of("O1", "O5", "U1"),
                labels(vectorStore.similaritySearch(request().filterExpression(ragToolFilter(false)).build())));
        assertEquals(Set.of("O1", "O2", "O3", "O4", "O5", "U1"),
                labels(vectorStore.similaritySearch(request().filterExpression(ragToolFilter(true)).build())));
    }

    @Test
    void ragToolFilterWorksWithBm25() {
        persistRagToolFixtures();
        // Documents de remplissage pour donner un IDF significatif au terme recherché
        for (int i = 0; i < 30; i++) {
            save("F" + i + " document de remplissage", Map.of("source", "s9", "organizations", "", "user_uuid", ""));
        }
        documentRepository.flush();

        var docs = new BM25DocumentRetriever(jdbcTemplate, 20, () -> ragToolFilter(false))
                .retrieve(new Query("cotisations"));

        assertEquals(Set.of("O1", "O5", "U1"), labels(docs));
        var o5 = docs.stream().filter(d -> label(d).equals("O5")).findFirst().orElseThrow();
        assertEquals(List.of("OFAS"), o5.getMetadata().get("organizations"));
    }

    @Test
    void tagsQueriesUnfoldArraysAndKeepLegacyStrings() {
        save("L", Map.of("source", "t1", "organizations", "", "tags", "a,b"));
        save("X", Map.of("source", "t1", "organizations", "", "tags", List.of("x", "y")));
        save("E", Map.of("source", "t1", "organizations", "", "tags", List.of()));
        save("N", Map.of("source", "t1", "organizations", ""));
        save("Z", Map.of("source", "t1", "organizations", "ZAS", "tags", List.of("z")));
        save("W", Map.of("source", "t2", "tags", List.of("w", "x")));
        documentRepository.flush();

        assertEquals(setOf("a,b", "x", "y", null, "z"), new HashSet<>(documentRepository.findTagsBySources(List.of("t1"))));
        assertEquals(setOf("a,b", "x", "y", null), new HashSet<>(documentRepository.findPublicTagsBySources(List.of("t1"))));
        assertEquals(setOf("a,b", "x", "y", null, "z", "w"), new HashSet<>(documentRepository.findAllTags()));
        assertEquals(setOf("a,b", "x", "y", null, "w"), new HashSet<>(documentRepository.findPublicTags()));
        assertEquals(documentRepository.findAllTags().size(), new HashSet<>(documentRepository.findAllTags()).size());
    }

    @Test
    void nativeQueriesOnScalarMetadataAreUnchanged() {
        save("P", Map.of("source", "s1", "user_uuid", "", "title", "T1", "url", "u1"));
        save("Q", Map.of("source", "s1", "user_uuid", "u1", "title", "T2", "url", "u2"));
        save("R", Map.of("source", "s1", "user_uuid", "u2", "title", "T3", "url", "u3"));
        save("S", Map.of("source", "s2", "answer_id", "answer-1", "title", "T4"));
        documentRepository.flush();
        entityManager.clear();

        assertEquals(Set.of("T1", "T2"), documentRepository.findDistinctContentsBySourceAndUser("s1", "u1").stream()
                .map(DocumentRepository.SourceContentProjection::getTitle).collect(Collectors.toSet()));
        assertEquals(Set.of("T1", "T2", "T3"), Set.copyOf(documentRepository.findDistinctTitlesBySource("s1")));
        assertEquals(Set.of("s1", "s2"), Set.copyOf(documentRepository.findAllSources()));
        assertEquals("T4", MetadataValues.getString(documentRepository.findByAnswerId("answer-1").getMetadata(), "title"));
    }

    @Test
    void faqStoresTagsAsJsonArray() {
        var item = faqService.save(new FAQItemLight(null, "Question ?", "Réponse.", "http://faq", "fr", List.of("AVS", "AI")));
        entityManager.flush();
        entityManager.clear();

        var question = questionRepository.findAll().stream()
                .filter(q -> q.getId().toString().equals(item.id())).findFirst().orElseThrow();
        var answerId = MetadataValues.getString(question.getMetadata(), "answer_id");
        var answer = documentRepository.findByAnswerId(answerId);

        assertEquals(List.of("AVS", "AI"), question.getMetadata().get("tags"));
        assertEquals(List.of("AVS", "AI"), answer.getMetadata().get("tags"));
        assertEquals("fr", item.language());
        assertEquals("http://faq", item.answer().url());
        assertEquals(Set.of("AVS", "AI"), Set.copyOf(documentRepository.findTagsBySources(List.of("knowledge_base"))));
    }

    /**
     * Sources s1/s2, utilisateur u1. Sans accès interne, seuls O1, O5 et U1 sont attendus.
     */
    private void persistRagToolFixtures() {
        save("O1 cotisations cotisations", Map.of("source", "s1", "organizations", "", "user_uuid", ""));
        save("O2 cotisations cotisations", Map.of("source", "s1", "organizations", "ZAS", "user_uuid", ""));
        // Clé "organizations" absente : exclue sans accès interne (comportement historique conservé)
        save("O3 cotisations cotisations", Map.of("source", "s1", "user_uuid", ""));
        save("O4 cotisations cotisations", Map.of("source", "s1", "organizations", List.of("ZAS", "OFAS"), "user_uuid", ""));
        save("O5 cotisations cotisations", Map.of("source", "s2", "organizations", List.of("OFAS"), "user_uuid", ""));
        save("U1 cotisations cotisations", Map.of("source", "s2", "organizations", "", "user_uuid", "u1"));
        save("U2 cotisations cotisations", Map.of("source", "s2", "organizations", "", "user_uuid", "u2"));
        save("S3 cotisations cotisations", Map.of("source", "s3", "organizations", "", "user_uuid", ""));
        documentRepository.flush();
    }

    private Filter.Expression ragToolFilter(boolean userHasAccessToInternalDocuments) {
        return RAGTool.metadataFilter(List.of("s1", "s2"), "u1", userHasAccessToInternalDocuments);
    }

    private DocumentEntity save(String content, Map<String, Object> metadata) {
        var entity = new DocumentEntity();
        entity.setContent(content);
        entity.setMetadata(metadata);
        entity.setEmbedding(EMBEDDING);
        return documentRepository.save(entity);
    }

    private static SearchRequest.Builder request() {
        return SearchRequest.builder().query("query").topK(50).similarityThresholdAll();
    }

    private static String label(Document document) {
        return document.getText().split(" ")[0];
    }

    private static Set<String> labels(List<Document> documents) {
        return documents.stream().map(DocumentMetadataIT::label).collect(Collectors.toSet());
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
