package zas.admin.zec.backend.rag;

import java.util.*;

/**
 * Trace d'un tour de l'agent, persistée sur le message assistant :
 * <ul>
 *   <li>{@code chat_history.consulted_sources} (jsonb) : statut déclaré par le LLM ({@link TurnStatus}), identifiants
 *       déclarés dans {@code used} et leur classement ({@link UsedDocuments}), documents consultés.
 *       Format : {@code {"turn_status": "answer|followup|refusal|unknown", "used": [...], "used_current": [...],
 *       "used_reused": [...], "used_unknown": [...], "sources": [ {...}, ... ]}} (clés {@code used*} absentes si
 *       l'agent n'a rien déclaré).</li>
 *   <li>{@code chat_history.tool_calls} (jsonb) : appels de tools du tour ({@link ToolCallRecord}), non relus.</li>
 *   <li>{@code chat_history.usage} (jsonb) : tokens consommés par le tour ({@link TurnUsage}), non relus.</li>
 * </ul>
 *
 * @param used      identifiants déclarés par l'agent dans {@code <turn_meta used="...">} et leur classement
 * @param toolCalls appels de tools du tour ; non persistés dans {@code consulted_sources}, donc vides après relecture
 * @param usage     tokens du tour ; {@code null} si non mesurés (et après relecture)
 */
public record TurnTrace(TurnStatus status, List<ConsultedSource> sources, UsedDocuments used,
                        List<ToolCallRecord> toolCalls, TurnUsage usage) {

    private static final String KEY_STATUS = "turn_status";
    private static final String KEY_USED = "used";
    private static final String KEY_USED_CURRENT = "used_current";
    private static final String KEY_USED_REUSED = "used_reused";
    private static final String KEY_USED_UNKNOWN = "used_unknown";
    private static final String KEY_SOURCES = "sources";

    public TurnTrace {
        status = status != null ? status : TurnStatus.UNKNOWN;
        sources = sources != null ? List.copyOf(sources) : List.of();
        used = used != null ? used : UsedDocuments.none();
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    public TurnTrace(TurnStatus status, List<ConsultedSource> sources, UsedDocuments used, List<ToolCallRecord> toolCalls) {
        this(status, sources, used, toolCalls, null);
    }

    public TurnTrace(TurnStatus status, List<ConsultedSource> sources) {
        this(status, sources, UsedDocuments.none(), List.of());
    }

    public static TurnTrace empty() {
        return new TurnTrace(TurnStatus.UNKNOWN, List.of());
    }

    public TurnTrace withToolCalls(List<ToolCallRecord> calls) {
        return new TurnTrace(status, sources, used, calls, usage);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KEY_STATUS, status.key());
        if (!used.isEmpty()) {
            map.put(KEY_USED, used.declared());
            map.put(KEY_USED_CURRENT, used.current());
            map.put(KEY_USED_REUSED, used.reused());
            map.put(KEY_USED_UNKNOWN, used.unknown());
        }
        map.put(KEY_SOURCES, sources.stream().map(ConsultedSource::toMap).toList());
        return map;
    }

    public TurnTrace withUsage(TurnUsage turnUsage) {
        return new TurnTrace(status, sources, used, toolCalls, turnUsage);
    }

    /** Contenu de {@code chat_history.tool_calls}. */
    public List<Map<String, Object>> toolCallsAsMaps() {
        return toolCalls.stream().map(ToolCallRecord::toMap).toList();
    }

    @SuppressWarnings("unchecked")
    public static TurnTrace fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return empty();
        }
        TurnStatus status = TurnStatus.fromKey(map.get(KEY_STATUS) instanceof String s ? s : null);
        List<ConsultedSource> sources = new ArrayList<>();
        if (map.get(KEY_SOURCES) instanceof Collection<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    sources.add(ConsultedSource.fromMap((Map<String, Object>) m));
                }
            }
        }
        var used = new UsedDocuments(strings(map.get(KEY_USED)), strings(map.get(KEY_USED_CURRENT)),
                strings(map.get(KEY_USED_REUSED)), strings(map.get(KEY_USED_UNKNOWN)));
        return new TurnTrace(status, sources, used, List.of());
    }

    private static List<String> strings(Object raw) {
        if (!(raw instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
}
