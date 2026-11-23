package zas.admin.zec.backend.rag;

import java.util.*;

/**
 * Citations d'un tour (F2a), tracées dans {@code chat_history.consulted_sources} : table des références attribuées aux
 * extraits lus ({@code citation_refs} : ref → id) et sources citées dans la réponse ({@code citations} :
 * numéro affiché, référence citée et id de l'extrait).
 */
public record TurnCitations(Map<Integer, String> refs, List<Cited> cited) {

    private static final String KEY_NUMBER = "n";
    private static final String KEY_REF = "ref";
    private static final String KEY_ID = "id";

    public record Cited(int number, int ref, String id) {}

    public TurnCitations {
        refs = refs != null ? Collections.unmodifiableMap(new TreeMap<>(refs)) : Map.of();
        cited = cited != null ? List.copyOf(cited) : List.of();
    }

    public static TurnCitations none() {
        return new TurnCitations(Map.of(), List.of());
    }

    public static TurnCitations of(CitationRefs refs, List<CitationStreamFilter.Citation> citations) {
        return new TurnCitations(refs.table(), citations.stream()
                .map(c -> new Cited(c.number(), c.ref(), c.source().id()))
                .toList());
    }

    public boolean isEmpty() {
        return refs.isEmpty() && cited.isEmpty();
    }

    Map<String, String> refsAsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        refs.forEach((ref, id) -> map.put(String.valueOf(ref), id));
        return map;
    }

    List<Map<String, Object>> citedAsMaps() {
        return cited.stream()
                .<Map<String, Object>>map(c -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put(KEY_NUMBER, c.number());
                    map.put(KEY_REF, c.ref());
                    map.put(KEY_ID, c.id());
                    return map;
                })
                .toList();
    }

    static TurnCitations fromMaps(Object rawRefs, Object rawCited) {
        Map<Integer, String> refs = new TreeMap<>();
        if (rawRefs instanceof Map<?, ?> map) {
            map.forEach((ref, id) -> {
                Integer number = toInt(ref);
                if (number != null && id != null) {
                    refs.put(number, String.valueOf(id));
                }
            });
        }
        List<Cited> cited = new ArrayList<>();
        if (rawCited instanceof Collection<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> map) {
                    Integer number = toInt(map.get(KEY_NUMBER));
                    Integer ref = toInt(map.get(KEY_REF));
                    Object id = map.get(KEY_ID);
                    if (number != null && ref != null && id != null) {
                        cited.add(new Cited(number, ref, String.valueOf(id)));
                    }
                }
            }
        }
        return new TurnCitations(refs, cited);
    }

    private static Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.valueOf(String.valueOf(value)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
