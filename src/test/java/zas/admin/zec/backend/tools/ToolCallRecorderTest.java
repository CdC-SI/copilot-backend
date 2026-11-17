package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.config.properties.ToolApprovalProperties;
import zas.admin.zec.backend.rag.ToolCallRecord;
import zas.admin.zec.backend.rag.token.ApprovalRequiredToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.token.ToolCallToken;
import zas.admin.zec.backend.rag.token.ToolResultToken;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Journal des appels de tools : étapes, arguments, statuts, documents retournés ; événements et approbation (T1). */
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

    // --- T1 : événements tool_call / tool_result et approbation ---

    private static final String USER = "user";
    private static final String CONVERSATION = "conv";

    private final BlockingQueue<Token> emitted = new LinkedBlockingQueue<>();

    private ToolContext contextWithSink(String userId, String conversationId) {
        Sinks.Many<Token> sink = Sinks.many().unicast().onBackpressureBuffer();
        sink.asFlux().subscribe(emitted::add);
        Map<String, Object> context = new HashMap<>();
        context.put(ToolContextKeys.CTX_STATUS_SINK, sink);
        if (userId != null) {
            context.put(ToolContextKeys.CTX_USER_ID, userId);
        }
        if (conversationId != null) {
            context.put(ToolContextKeys.CTX_CONVERSATION_ID, conversationId);
        }
        return new ToolContext(context);
    }

    private static ToolApprovalService approvalsFor(Duration timeout, String... tools) {
        return new ToolApprovalService(new ToolApprovalProperties(List.of(tools), timeout));
    }

    private ApprovalRequiredToken nextApprovalRequest() throws InterruptedException {
        Token token;
        do {
            token = emitted.poll(5, TimeUnit.SECONDS);
            assertNotNull(token, "approval_required attendu");
        } while (!(token instanceof ApprovalRequiredToken));
        return (ApprovalRequiredToken) token;
    }

    @Test
    void emitsToolCallThenToolResultWithTheSameId() {
        var search = recorder.wrap(tool("search", (in, ctx) -> deposit("<document id=\"a\">…</document>", "a", "b")))[0];
        var ctx = contextWithSink(USER, CONVERSATION);

        search.call("{\"query\":\"décès\"}", ctx);
        search.call("{\"query\":\"rente\"}", ctx);

        var tokens = List.copyOf(emitted);
        assertEquals(4, tokens.size());
        assertEquals(new ToolCallToken("1", "search", Map.of("query", "décès")), tokens.get(0));
        var result = assertInstanceOf(ToolResultToken.class, tokens.get(1));
        assertEquals("1", result.callId());
        assertEquals("search", result.tool());
        assertEquals(ToolCallRecord.STATUS_OK, result.status());
        assertEquals(2, result.documentCount());
        assertEquals("2", assertInstanceOf(ToolCallToken.class, tokens.get(2)).callId());
        assertEquals("2", assertInstanceOf(ToolResultToken.class, tokens.get(3)).callId());
    }

    @Test
    void emitsAnErrorResultWhenTheToolThrows() {
        var failing = recorder.wrap(tool("boom", (in, ctx) -> {
            throw new IllegalStateException("db down");
        }))[0];

        assertThrows(IllegalStateException.class, () -> failing.call("{}", contextWithSink(USER, CONVERSATION)));

        var result = assertInstanceOf(ToolResultToken.class, List.copyOf(emitted).get(1));
        assertEquals(ToolCallRecord.STATUS_ERROR, result.status());
    }

    @Test
    void truncatesLongTextualArgumentsInEventsOnly() {
        String longText = "x".repeat(ToolCallRecorder.MAX_EVENT_ARGUMENT_LENGTH + 50);
        var search = recorder.wrap(tool("search", (in, ctx) -> "ok"))[0];

        search.call("{\"query\":\"" + longText + "\",\"filters\":{\"note\":\"" + longText + "\"},\"limit\":3}",
                contextWithSink(USER, CONVERSATION));

        var arguments = assertInstanceOf(ToolCallToken.class, List.copyOf(emitted).getFirst()).arguments();
        assertEquals(ToolCallRecorder.MAX_EVENT_ARGUMENT_LENGTH + 1, ((String) arguments.get("query")).length());
        assertEquals(ToolCallRecorder.MAX_EVENT_ARGUMENT_LENGTH + 1,
                ((String) ((Map<?, ?>) arguments.get("filters")).get("note")).length());
        assertEquals(3, arguments.get("limit"));
        assertEquals(longText, recorder.calls().getFirst().arguments().get("query"));
    }

    @Test
    void toolNotRequiringApprovalRunsImmediately() {
        var approvals = approvalsFor(Duration.ofSeconds(5), "get_attachment_content");
        var approvalRecorder = new ToolCallRecorder(turnDocuments, approvals);
        var search = approvalRecorder.wrap(tool("search", (in, ctx) -> "ok"))[0];

        assertEquals("ok", search.call("{}", contextWithSink(USER, CONVERSATION)));
        assertTrue(emitted.stream().noneMatch(ApprovalRequiredToken.class::isInstance));
    }

    @Test
    void approvedCallIsExecutedAfterTheDecision() throws Exception {
        var approvals = approvalsFor(Duration.ofSeconds(5), "get_attachment_content");
        var approvalRecorder = new ToolCallRecorder(turnDocuments, approvals);
        var executed = new AtomicBoolean();
        var attachment = approvalRecorder.wrap(tool("get_attachment_content", (in, ctx) -> {
            executed.set(true);
            return "contenu";
        }))[0];
        var ctx = contextWithSink(USER, CONVERSATION);

        var result = CompletableFuture.supplyAsync(() -> attachment.call("{\"attachmentId\":1}", ctx));
        var request = nextApprovalRequest();
        assertEquals("1", request.callId());
        assertEquals("get_attachment_content", request.tool());
        assertEquals(Map.of("attachmentId", 1), request.arguments());
        assertNotNull(request.expiresAt());
        assertFalse(executed.get());

        assertTrue(approvals.decide(USER, CONVERSATION, request.approvalId(), true));

        assertEquals("contenu", result.get(5, TimeUnit.SECONDS));
        assertTrue(executed.get());
        assertEquals(ToolCallRecord.STATUS_OK, approvalRecorder.calls().getFirst().status());
        assertEquals(ToolCallRecord.STATUS_OK,
                assertInstanceOf(ToolResultToken.class, List.copyOf(emitted).getLast()).status());
    }

    @Test
    void rejectedCallIsNotExecutedAndTheLlmIsTold() throws Exception {
        var approvals = approvalsFor(Duration.ofSeconds(5), "get_attachment_content");
        var approvalRecorder = new ToolCallRecorder(turnDocuments, approvals);
        var executed = new AtomicBoolean();
        var attachment = approvalRecorder.wrap(tool("get_attachment_content", (in, ctx) -> {
            executed.set(true);
            return "contenu";
        }))[0];
        var ctx = contextWithSink(USER, CONVERSATION);

        var result = CompletableFuture.supplyAsync(() -> attachment.call("{}", ctx));
        var request = nextApprovalRequest();
        assertFalse(approvals.decide("intrus", CONVERSATION, request.approvalId(), true), "autre utilisateur");
        assertFalse(approvals.decide(USER, "autre", request.approvalId(), true), "autre conversation");
        assertTrue(approvals.decide(USER, CONVERSATION, request.approvalId(), false));

        assertEquals(ToolCallRecorder.REJECTED_RESULT, result.get(5, TimeUnit.SECONDS));
        assertFalse(executed.get());
        assertEquals(ToolCallRecord.STATUS_REJECTED, approvalRecorder.calls().getFirst().status());
        assertEquals(ToolCallRecord.STATUS_REJECTED,
                assertInstanceOf(ToolResultToken.class, List.copyOf(emitted).getLast()).status());
        assertFalse(approvals.decide(USER, CONVERSATION, request.approvalId(), true), "demande déjà tranchée");
    }

    @Test
    void unansweredCallExpires() {
        var approvals = approvalsFor(Duration.ofMillis(100), "get_attachment_content");
        var approvalRecorder = new ToolCallRecorder(turnDocuments, approvals);
        var attachment = approvalRecorder.wrap(tool("get_attachment_content", (in, ctx) -> fail("non approuvé")))[0];

        assertEquals(ToolCallRecorder.NOT_APPROVED_RESULT, attachment.call("{}", contextWithSink(USER, CONVERSATION)));

        assertEquals(ToolCallRecord.STATUS_EXPIRED, approvalRecorder.calls().getFirst().status());
        var request = assertInstanceOf(ApprovalRequiredToken.class, List.copyOf(emitted).get(1));
        assertFalse(approvals.decide(USER, CONVERSATION, request.approvalId(), true), "décision après expiration");
    }

    @Test
    void stoppingTheGenerationCancelsPendingApprovals() throws Exception {
        var approvals = approvalsFor(Duration.ofSeconds(30), "get_attachment_content");
        var approvalRecorder = new ToolCallRecorder(turnDocuments, approvals);
        var attachment = approvalRecorder.wrap(tool("get_attachment_content", (in, ctx) -> fail("non approuvé")))[0];
        var ctx = contextWithSink(USER, CONVERSATION);

        var result = CompletableFuture.supplyAsync(() -> attachment.call("{}", ctx));
        nextApprovalRequest();
        approvals.cancelAll("intrus", CONVERSATION);
        assertFalse(result.isDone());
        approvals.cancelAll(USER, CONVERSATION);

        assertEquals(ToolCallRecorder.NOT_APPROVED_RESULT, result.get(5, TimeUnit.SECONDS));
        assertEquals(ToolCallRecord.STATUS_CANCELLED, approvalRecorder.calls().getFirst().status());
    }

    @Test
    void callWithoutOwnerCannotBeApproved() {
        var approvals = approvalsFor(Duration.ofSeconds(30), "get_attachment_content");
        var approvalRecorder = new ToolCallRecorder(turnDocuments, approvals);
        var attachment = approvalRecorder.wrap(tool("get_attachment_content", (in, ctx) -> fail("non approuvé")))[0];

        assertEquals(ToolCallRecorder.NOT_APPROVED_RESULT, attachment.call("{}", contextWithSink(USER, null)));
        assertEquals(ToolCallRecord.STATUS_CANCELLED, approvalRecorder.calls().getFirst().status());
    }
}
