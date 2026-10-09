package zas.admin.zec.backend.process;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.LocalConfig;
import zas.admin.zec.backend.persistence.entity.BusinessProcessEntity;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.BusinessProcessRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migrations V17 ({@code business_process}) et V18 ({@code chat_history.processes}) : recherches
 * jsonb de {@link BusinessProcessRepository}, lecture par {@link ProcessService}, et persistance des
 * processus d'un message. Chaque test s'exécute dans une transaction annulée.
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
class BusinessProcessRepositoryIT {

    private static final String BPANDA = "ID-0123456789abcdef0123456789abcdef";

    @Autowired
    private BusinessProcessRepository repository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ProcessService processService;

    @PersistenceContext
    private EntityManager entityManager;

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
    void jsonbLookupsByBpandaIdAndName() {
        save("bpmn-main", """
                {"process_name": "CSC AF - Traiter un exemple", "process_state": "Brouillon", "bpanda_id": "%s",
                 "nodes": [{"id": "n1", "kind": "CallActivity", "called_bpmn_id": "bpmn-sub"}], "arcs": []}
                """.formatted(BPANDA));
        save("bpmn-sub", """
                {"process_name": "CSC AF - Vérifier un exemple", "bpanda_id": null, "nodes": [], "arcs": []}
                """);
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of("bpmn-main"), repository.findByBpandaIdIn(List.of(BPANDA)).stream()
                .map(BusinessProcessEntity::getBpmnId).toList());
        assertEquals(List.of("CSC AF - Traiter un exemple", "CSC AF - Vérifier un exemple"),
                repository.findAllNames().stream().map(BusinessProcessRepository.ProcessNameView::getProcessName).toList());

        var main = processService.findByBpandaIds(List.of(BPANDA)).getFirst();
        assertEquals("Brouillon", main.state());
        assertEquals(List.of("bpmn-sub"), processService.resolveCalledProcesses(main).stream().map(BusinessProcess::bpmnId).toList());
        assertTrue(processService.findByName("traiter un exemple").found());
    }

    @Test
    void messageProcessesRoundTrip() {
        conversationRepository.save(message("avec", new String[]{"bpmn-main"}));
        conversationRepository.save(message("sans", null));
        entityManager.flush();
        entityManager.clear();

        var messages = conversationRepository.findByConversationIdAndUserIdOrderByTimestamp("conv", "user", Limit.unlimited());

        assertArrayEquals(new String[]{"bpmn-main"}, messages.get(0).getProcesses());
        assertNull(messages.get(1).getProcesses());
    }

    private void save(String bpmnId, String content) {
        var entity = new BusinessProcessEntity();
        entity.setBpmnId(bpmnId);
        entity.setContent(content);
        repository.save(entity);
    }

    private static MessageEntity message(String text, String[] processes) {
        var entity = new MessageEntity();
        entity.setUserId("user");
        entity.setConversationId("conv");
        entity.setMessageId(text);
        entity.setRole("assistant");
        entity.setMessage(text);
        entity.setLanguage("fr");
        entity.setTimestamp(LocalDateTime.now().plusNanos(text.equals("avec") ? 0 : 1_000_000));
        entity.setSources(new String[0]);
        entity.setSuggestions(new String[0]);
        entity.setProcesses(processes);
        return entity;
    }
}
