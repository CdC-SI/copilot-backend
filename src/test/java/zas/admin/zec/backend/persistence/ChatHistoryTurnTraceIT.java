package zas.admin.zec.backend.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.ToolCallRecord;
import zas.admin.zec.backend.rag.TurnStatus;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.TurnUsage;
import zas.admin.zec.backend.rag.UsedDocuments;
import zas.admin.zec.backend.tools.document.AbstractDocumentLookupIT;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistance de la trace du tour sur {@code chat_history} (V19 : {@code consulted_sources}, {@code tool_calls}) :
 * relecture JPA et requêtes jsonb utilisables par les évaluations automatisées.
 */
class ChatHistoryTurnTraceIT extends AbstractDocumentLookupIT {

    @Autowired
    private ConversationRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void turnTraceAndToolCallsRoundTripAndCanBeQueried() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("collection", "BUSINESS_PROCESS");
        arguments.put("filters", List.of(Map.of("key", "process_id", "op", "EQ", "value", "CSC AF-61-2-408")));
        arguments.put("limit", null);
        var calls = List.of(
                new ToolCallRecord(1, 1, "search_social_insurance_documentation", Map.of("query", "décès"),
                        ToolCallRecord.STATUS_OK, 812, 5400, List.of("d1")),
                new ToolCallRecord(2, 2, "get_documents", arguments, ToolCallRecord.STATUS_OK, 15, 7900, List.of("p1")));
        var process = new ConsultedSource("p1", "business_process", "business_process", "Processus", "https://bpanda/p",
                null, null, "2026.03.02", "Brouillon", "CSC AF-61-2-408", null, List.of());
        var used = new UsedDocuments(List.of("p1", "old-1"), List.of("p1"), List.of("old-1"), List.of());
        var usage = new TurnUsage(List.of(new TurnUsage.LlmCall("agent", 9000, 300, null)),
                List.of(new TurnUsage.LlmCall("workspace_inference", 700, 4, null)));
        var trace = new TurnTrace(TurnStatus.ANSWER, List.of(process), used, calls, usage);

        var entity = new MessageEntity();
        entity.setUserId("user-it");
        entity.setConversationId("conv-it");
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setRole("LLM");
        entity.setMessage("Réponse");
        entity.setLanguage("fr");
        entity.setTimestamp(LocalDateTime.now());
        entity.setConsultedSources(trace.toMap());
        entity.setToolCalls(trace.toolCallsAsMaps());
        entity.setUsage(trace.usage().toMap());
        Long id = repository.saveAndFlush(entity).getId();
        entityManager.clear();

        var read = repository.findById(id).orElseThrow();
        assertEquals(trace.withToolCalls(List.of()).withUsage(null), TurnTrace.fromMap(read.getConsultedSources()));
        assertEquals(2, read.getToolCalls().size());
        var second = read.getToolCalls().get(1);
        assertEquals("get_documents", second.get("tool"));
        assertEquals(2, ((Number) second.get("step")).intValue());
        assertTrue(((Map<?, ?>) second.get("arguments")).containsKey("limit"));
        assertEquals(List.of("p1"), second.get("document_ids"));
        assertEquals(2, ((Number) read.getUsage().get("llm_calls")).intValue());
        assertEquals(9700, ((Number) read.getUsage().get("prompt_tokens")).intValue());

        // Requêtes types d'une évaluation : tours ayant ouvert un processus, nombre d'appels par tour.
        Integer openedProcess = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM chat_history
                WHERE tool_calls @> '[{"tool": "get_documents", "arguments": {"collection": "BUSINESS_PROCESS"}}]'::jsonb
                  AND consulted_sources @> '{"turn_status": "answer", "used_current": ["p1"], "used_reused": ["old-1"]}'::jsonb""", Integer.class);
        Integer callsCount = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(tool_calls) FROM chat_history WHERE id = ?", Integer.class, id);
        assertEquals(1, openedProcess);
        assertEquals(2, callsCount);
    }
}
