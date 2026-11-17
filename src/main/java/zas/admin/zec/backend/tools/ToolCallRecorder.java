package zas.admin.zec.backend.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.rag.ToolCallRecord;
import zas.admin.zec.backend.rag.token.ApprovalRequiredToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.token.ToolCallToken;
import zas.admin.zec.backend.rag.token.ToolResultToken;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Journal des appels de tools d'un tour, persisté dans {@code chat_history.tool_calls} (débogage, évaluations).
 *
 * <p>Les callbacks des tools sont enveloppés ({@link #wrap}) : chaque appel est enregistré avec les arguments fournis
 * par le LLM, son statut, sa durée, la taille du résultat et les documents qu'il a déposés dans la liste partagée du
 * tour ({@link ToolContextKeys#CTX_RETRIEVED_DOCUMENTS}).</p>
 *
 * <p>T1 : chaque appel est aussi annoncé en temps réel dans le sink de statut du contexte
 * ({@link ToolContextKeys#CTX_STATUS_SINK}) : {@link ToolCallToken} avant l'exécution, {@link ToolResultToken} après.
 * Un tool soumis à approbation ({@link ToolApprovalService}) n'est exécuté qu'après l'accord de l'utilisateur
 * ({@link ApprovalRequiredToken}) ; sinon le LLM reçoit un refus et l'appel est journalisé comme refusé, expiré ou
 * annulé.</p>
 *
 * <p>Spring AI exécute séquentiellement les appels d'une même étape et crée un {@link ToolContext} par étape de la
 * boucle agentique : un changement d'instance de contexte marque une nouvelle étape. Une instance par tour.</p>
 */
public final class ToolCallRecorder {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    /** Longueur maximale d'une valeur d'argument textuelle dans les événements (le journal persisté reste complet). */
    static final int MAX_EVENT_ARGUMENT_LENGTH = 300;

    static final String REJECTED_RESULT = """
            <tool_call_rejected>
            The user declined this tool call. It was NOT executed. Do not call this tool again with the same \
            arguments. Answer without its result and briefly tell the user that the action was not performed.
            </tool_call_rejected>
            """;
    static final String NOT_APPROVED_RESULT = """
            <tool_call_not_approved>
            This tool call required the user's approval, which was not given in time. It was NOT executed. \
            Answer without its result and briefly tell the user that the action was not performed.
            </tool_call_not_approved>
            """;

    private final List<Document> turnDocuments;
    private final ToolApprovalService approvals;
    private final List<ToolCallRecord> calls = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private ToolContext lastContext;
    private int step;

    /** @param turnDocuments liste partagée des documents du tour (alimentée par les tools) */
    public ToolCallRecorder(List<Document> turnDocuments) {
        this(turnDocuments, null);
    }

    /**
     * @param turnDocuments liste partagée des documents du tour (alimentée par les tools)
     * @param approvals     politique d'approbation des tools ({@code null} : aucune approbation)
     */
    public ToolCallRecorder(List<Document> turnDocuments, ToolApprovalService approvals) {
        this.turnDocuments = turnDocuments;
        this.approvals = approvals;
    }

    public ToolCallback[] wrap(ToolCallback... callbacks) {
        return Arrays.stream(callbacks).map(RecordingToolCallback::new).toArray(ToolCallback[]::new);
    }

    /** Appels enregistrés, dans l'ordre d'exécution. */
    public List<ToolCallRecord> calls() {
        return List.copyOf(calls);
    }

    private synchronized int stepOf(ToolContext context) {
        if (step == 0 || context != lastContext) {
            step++;
            lastContext = context;
        }
        return step;
    }

    private ToolCallRecord record(int callSequence, int callStep, String tool, String input, String status,
                                  long startNanos, int resultLength, int documentsBefore) {
        List<Document> snapshot = List.copyOf(turnDocuments);
        List<String> documentIds = snapshot.subList(Math.min(documentsBefore, snapshot.size()), snapshot.size())
                .stream().map(Document::getId).filter(Objects::nonNull).distinct().toList();
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        var call = new ToolCallRecord(callSequence, callStep, tool, arguments(input), status, durationMs,
                resultLength, documentIds);
        calls.add(call);
        return call;
    }

    static Map<String, Object> arguments(String input) {
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(input, MAP_TYPE);
        } catch (JacksonException e) {
            return Map.of("_raw", input);
        }
    }

    static String statusOf(String result) {
        String text = result == null ? "" : result.strip();
        if (text.startsWith("<tool_call_limit_reached>")) {
            return ToolCallRecord.STATUS_BUDGET_EXHAUSTED;
        }
        if (text.startsWith("<error>")) {
            return ToolCallRecord.STATUS_INVALID_ARGUMENTS;
        }
        if (text.contains("<no_document_found>") || text.contains("<no_documentation_found>")) {
            return ToolCallRecord.STATUS_NO_RESULT;
        }
        return ToolCallRecord.STATUS_OK;
    }

    /** Arguments affichables : valeurs textuelles tronquées, structure conservée. */
    static Map<String, Object> eventArguments(Map<String, Object> arguments) {
        Map<String, Object> truncated = new LinkedHashMap<>();
        arguments.forEach((key, value) -> truncated.put(key, truncate(value)));
        return Collections.unmodifiableMap(truncated);
    }

    private static Object truncate(Object value) {
        return switch (value) {
            case String text when text.length() > MAX_EVENT_ARGUMENT_LENGTH ->
                    text.substring(0, MAX_EVENT_ARGUMENT_LENGTH) + "…";
            case Map<?, ?> map -> {
                Map<Object, Object> copy = new LinkedHashMap<>();
                map.forEach((key, nested) -> copy.put(key, truncate(nested)));
                yield copy;
            }
            case List<?> list -> list.stream().map(ToolCallRecorder::truncate).toList();
            case null, default -> value;
        };
    }

    @SuppressWarnings("unchecked")
    private static void emit(ToolContext toolContext, Token token) {
        if (toolContext != null && toolContext.getContext().get(ToolContextKeys.CTX_STATUS_SINK) instanceof Sinks.Many<?> sink) {
            ((Sinks.Many<Token>) sink).tryEmitNext(token);
        }
    }

    private static String contextValue(ToolContext toolContext, String key) {
        return toolContext != null && toolContext.getContext().get(key) instanceof String value ? value : null;
    }

    private final class RecordingToolCallback implements ToolCallback {

        private final ToolCallback delegate;

        private RecordingToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            int callStep = stepOf(toolContext);
            int callSequence = sequence.incrementAndGet();
            String callId = String.valueOf(callSequence);
            String tool = delegate.getToolDefinition().name();
            Map<String, Object> displayedArguments = eventArguments(arguments(toolInput));
            emit(toolContext, new ToolCallToken(callId, tool, displayedArguments));

            if (approvals != null && approvals.requiresApproval(tool)) {
                String refusal = awaitApproval(toolContext, callId, tool, displayedArguments);
                if (refusal != null) {
                    String result = ToolCallRecord.STATUS_REJECTED.equals(refusal) ? REJECTED_RESULT : NOT_APPROVED_RESULT;
                    finish(record(callSequence, callStep, tool, toolInput, refusal, System.nanoTime(), result.length(),
                            turnDocuments.size()), toolContext, callId);
                    return result;
                }
            }

            int documentsBefore = turnDocuments.size();
            long start = System.nanoTime();
            try {
                String result = delegate.call(toolInput, toolContext);
                finish(record(callSequence, callStep, tool, toolInput, statusOf(result), start,
                        result == null ? 0 : result.length(), documentsBefore), toolContext, callId);
                return result;
            } catch (RuntimeException e) {
                finish(record(callSequence, callStep, tool, toolInput, ToolCallRecord.STATUS_ERROR, start, 0,
                        documentsBefore), toolContext, callId);
                throw e;
            }
        }

        /** @return {@code null} si l'appel est approuvé, sinon le statut de refus ({@code ToolCallRecord.STATUS_*}) */
        private String awaitApproval(ToolContext toolContext, String callId, String tool,
                                     Map<String, Object> displayedArguments) {
            String userId = contextValue(toolContext, ToolContextKeys.CTX_USER_ID);
            String conversationId = contextValue(toolContext, ToolContextKeys.CTX_CONVERSATION_ID);
            if (userId == null || conversationId == null) {
                return ToolCallRecord.STATUS_CANCELLED;
            }
            var request = approvals.open(userId, conversationId);
            emit(toolContext, new ApprovalRequiredToken(callId, request.approvalId(), tool, displayedArguments,
                    request.expiresAt()));
            return switch (approvals.await(request.approvalId())) {
                case APPROVED -> null;
                case REJECTED -> ToolCallRecord.STATUS_REJECTED;
                case EXPIRED -> ToolCallRecord.STATUS_EXPIRED;
                case CANCELLED -> ToolCallRecord.STATUS_CANCELLED;
            };
        }

        private void finish(ToolCallRecord call, ToolContext toolContext, String callId) {
            emit(toolContext, new ToolResultToken(callId, call.tool(), call.status(), call.durationMs(),
                    call.documentIds().size()));
        }
    }
}
