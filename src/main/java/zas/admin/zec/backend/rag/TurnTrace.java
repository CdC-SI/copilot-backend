package zas.admin.zec.backend.rag;

import java.util.*;

/**
 * Trace d'un tour de l'agent, persistée dans {@code chat_history.consulted_sources} (jsonb) sur le
 * message assistant : statut déclaré par le LLM ({@link TurnStatus}) et documents consultés.
 *
 * <p>Format JSON : {@code {"turn_status": "answer|followup|refusal|unknown", "sources": [ {...}, ... ]}}.</p>
 */
public record TurnTrace(TurnStatus status, List<ConsultedSource> sources) {

    private static final String KEY_STATUS = "turn_status";
    private static final String KEY_SOURCES = "sources";

    public TurnTrace {
        status = status != null ? status : TurnStatus.UNKNOWN;
        sources = sources != null ? List.copyOf(sources) : List.of();
    }

    public static TurnTrace empty() {
        return new TurnTrace(TurnStatus.UNKNOWN, List.of());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KEY_STATUS, status.key());
        map.put(KEY_SOURCES, sources.stream().map(ConsultedSource::toMap).toList());
        return map;
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
        return new TurnTrace(status, sources);
    }
}
