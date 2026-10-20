package zas.admin.zec.backend.rag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Appel de tool effectué par l'agent pendant un tour, persisté dans {@code chat_history.tool_calls} (jsonb) sur le
 * message assistant, pour le débogage et les évaluations automatisées.
 *
 * @param sequence     rang de l'appel dans le tour (1, 2, ...)
 * @param step         étape de la boucle agentique (les appels d'une même étape ont été demandés ensemble par le LLM)
 * @param tool         nom du tool
 * @param arguments    arguments tels que fournis par le LLM (JSON décodé ; {@code {"_raw": "..."}} s'il est invalide)
 * @param status       {@code ok}, {@code no_result}, {@code invalid_arguments}, {@code budget_exhausted} ou {@code error}
 * @param durationMs   durée d'exécution du tool
 * @param resultLength nombre de caractères retournés au LLM
 * @param documentIds  documents retournés par l'appel (identifiants exploitables par {@code get_documents} ou pièces jointes)
 */
public record ToolCallRecord(int sequence, int step, String tool, Map<String, Object> arguments, String status,
                             long durationMs, int resultLength, List<String> documentIds) {

    public static final String STATUS_OK = "ok";
    public static final String STATUS_NO_RESULT = "no_result";
    public static final String STATUS_INVALID_ARGUMENTS = "invalid_arguments";
    public static final String STATUS_BUDGET_EXHAUSTED = "budget_exhausted";
    public static final String STATUS_ERROR = "error";

    public ToolCallRecord {
        // Copie tolérant les valeurs nulles (ex. "limit": null fourni par le LLM), contrairement à Map.copyOf.
        arguments = arguments != null ? Collections.unmodifiableMap(new LinkedHashMap<>(arguments)) : Map.of();
        documentIds = documentIds != null ? List.copyOf(documentIds) : List.of();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("seq", sequence);
        map.put("step", step);
        map.put("tool", tool);
        map.put("arguments", arguments);
        map.put("status", status);
        map.put("duration_ms", durationMs);
        map.put("result_chars", resultLength);
        map.put("document_ids", documentIds);
        return map;
    }
}
