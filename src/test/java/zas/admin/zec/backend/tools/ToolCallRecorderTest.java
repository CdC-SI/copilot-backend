package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import zas.admin.zec.backend.rag.ToolCallRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Journal des appels de tools : étapes, arguments, statuts, documents retournés. */
class ToolCallRecorderTest {

    private final List<Document> turnDocuments = new CopyOnWriteArrayList<>();
    private final ToolCallRecorder recorder = new ToolCallRecorder(turnDocuments);

    private ToolCallback tool(String name, BiFunction<String, ToolContext, String> behaviour) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, null);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return behaviour.apply(toolInput, toolContext);
            }
        };
    }

    private String deposit(String result, String... ids) {
        for (String id : ids) {
            turnDocuments.add(Document.builder().id(id).text("t").build());
        }
        return result;
    }

    @Test
    void recordsStepsArgumentsAndReturnedDocuments() {
        var search = recorder.wrap(tool("search", (in, ctx) -> deposit("<document id=\"a\">…</document>", "a", "b", "a")))[0];
        var get = recorder.wrap(tool("get_documents", (in, ctx) -> deposit("<document id=\"c\">…</document>", "c")))[0];
        var step1 = new ToolContext(new HashMap<>());
        var step2 = new ToolContext(new HashMap<>());

        search.call("{\"query\":\"décès\"}", step1);
        get.call("{\"collection\":\"BUSINESS_PROCESS\",\"limit\":null}", step1);
        get.call("{\"collection\":\"DOCUMENTATION\"}", step2);

        var calls = recorder.calls();
        assertEquals(List.of(1, 2, 3), calls.stream().map(ToolCallRecord::sequence).toList());
        assertEquals(List.of(1, 1, 2), calls.stream().map(ToolCallRecord::step).toList());
        assertEquals(List.of("search", "get_documents", "get_documents"), calls.stream().map(ToolCallRecord::tool).toList());
        assertEquals("décès", calls.getFirst().arguments().get("query"));
        assertTrue(calls.get(1).arguments().containsKey("limit"));
        assertNull(calls.get(1).arguments().get("limit"));
        assertEquals(List.of("a", "b"), calls.getFirst().documentIds());
        assertEquals(List.of("c"), calls.get(1).documentIds());
        assertEquals(ToolCallRecord.STATUS_OK, calls.getFirst().status());
        assertTrue(calls.getFirst().resultLength() > 0);
    }

    @Test
    void classifiesResults() {
        var budget = recorder.wrap(tool("a", (in, ctx) -> ToolCallBudget.EXHAUSTED_MESSAGE))[0];
        var invalid = recorder.wrap(tool("b", (in, ctx) -> "<error>Unknown key 'x'.</error>"))[0];
        var empty = recorder.wrap(tool("c", (in, ctx) -> "<notice>…</notice>\n<no_document_found>\n…\n</no_document_found>"))[0];
        var none = recorder.wrap(tool("d", (in, ctx) -> "<no_documentation_found>…</no_documentation_found>"))[0];
        var ctx = new ToolContext(new HashMap<>());

        budget.call("{}", ctx);
        invalid.call("{}", ctx);
        empty.call("{}", ctx);
        none.call("{}", ctx);

        assertEquals(List.of(ToolCallRecord.STATUS_BUDGET_EXHAUSTED, ToolCallRecord.STATUS_INVALID_ARGUMENTS,
                        ToolCallRecord.STATUS_NO_RESULT, ToolCallRecord.STATUS_NO_RESULT),
                recorder.calls().stream().map(ToolCallRecord::status).toList());
    }

    @Test
    void recordsAndRethrowsExceptionsAndKeepsInvalidJsonArguments() {
        var failing = recorder.wrap(tool("boom", (in, ctx) -> {
            throw new IllegalStateException("db down");
        }))[0];

        assertThrows(IllegalStateException.class, () -> failing.call("not json", new ToolContext(new HashMap<>())));

        var call = recorder.calls().getFirst();
        assertEquals(ToolCallRecord.STATUS_ERROR, call.status());
        assertEquals(Map.of("_raw", "not json"), call.arguments());
        assertEquals(0, call.resultLength());
    }

    @Test
    void exposesTheDelegateDefinitionAndSerializesForJsonb() {
        var wrapped = recorder.wrap(tool("search", (in, ctx) -> "ok"))[0];
        assertEquals("search", wrapped.getToolDefinition().name());

        wrapped.call("{\"query\":\"q\"}", new ToolContext(new HashMap<>()));
        Map<String, Object> map = recorder.calls().getFirst().toMap();

        assertEquals(List.of("seq", "step", "tool", "arguments", "status", "duration_ms", "result_chars", "document_ids"),
                List.copyOf(map.keySet()));
        assertEquals(Map.of("query", "q"), map.get("arguments"));
    }
}
