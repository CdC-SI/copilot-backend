package zas.admin.zec.backend.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.rag.ToolCallRecord;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Journal des appels de tools d'un tour, persisté dans {@code chat_history.tool_calls} (débogage, évaluations).
 *
 * <p>Les callbacks des tools sont enveloppés ({@link #wrap}) : chaque appel est enregistré avec les arguments fournis
 * par le LLM, son statut, sa durée, la taille du résultat et les documents qu'il a déposés dans la liste partagée du
 * tour ({@link ToolContextKeys#CTX_RETRIEVED_DOCUMENTS}).</p>
 *
 * <p>Spring AI exécute séquentiellement les appels d'une même étape et crée un {@link ToolContext} par étape de la
 * boucle agentique : un changement d'instance de contexte marque une nouvelle étape. Une instance par tour.</p>
 */
public final class ToolCallRecorder {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final List<Document> turnDocuments;
    private final List<ToolCallRecord> calls = new CopyOnWriteArrayList<>();
    private ToolContext lastContext;
    private int step;

    /** @param turnDocuments liste partagée des documents du tour (alimentée par les tools) */
    public ToolCallRecorder(List<Document> turnDocuments) {
        this.turnDocuments = turnDocuments;
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

    private void record(int callStep, String tool, String input, String status, long startNanos, int resultLength,
                        int documentsBefore) {
        List<Document> snapshot = List.copyOf(turnDocuments);
        List<String> documentIds = snapshot.subList(Math.min(documentsBefore, snapshot.size()), snapshot.size())
                .stream().map(Document::getId).filter(Objects::nonNull).distinct().toList();
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        calls.add(new ToolCallRecord(calls.size() + 1, callStep, tool, arguments(input), status, durationMs,
                resultLength, documentIds));
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
            String tool = delegate.getToolDefinition().name();
            int documentsBefore = turnDocuments.size();
            long start = System.nanoTime();
            try {
                String result = delegate.call(toolInput, toolContext);
                record(callStep, tool, toolInput, statusOf(result), start, result == null ? 0 : result.length(),
                        documentsBefore);
                return result;
            } catch (RuntimeException e) {
                record(callStep, tool, toolInput, ToolCallRecord.STATUS_ERROR, start, 0, documentsBefore);
                throw e;
            }
        }
    }
}
