package zas.admin.zec.backend.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Identifiants déclarés par l'agent dans {@code <turn_meta used="...">}, classés au moment de l'écriture de la réponse.
 *
 * @param declared identifiants tels que déclarés (ordre conservé)
 * @param current  documents consultés pendant ce tour
 * @param reused   documents de tours antérieurs de la conversation (traces ou sources affichées), non consultés ce tour
 * @param unknown  identifiants inconnus de la conversation (ignorés pour l'affichage)
 */
public record UsedDocuments(List<String> declared, List<String> current, List<String> reused, List<String> unknown) {

    public UsedDocuments {
        declared = declared != null ? List.copyOf(declared) : List.of();
        current = current != null ? List.copyOf(current) : List.of();
        reused = reused != null ? List.copyOf(reused) : List.of();
        unknown = unknown != null ? List.copyOf(unknown) : List.of();
    }

    public static UsedDocuments none() {
        return new UsedDocuments(List.of(), List.of(), List.of(), List.of());
    }

    /**
     * @param currentIds documents consultés pendant ce tour (prioritaires)
     * @param earlierIds documents connus des tours précédents
     */
    public static UsedDocuments classify(List<String> declared, Set<String> currentIds, Set<String> earlierIds) {
        List<String> distinct = declared.stream().distinct().toList();
        List<String> current = new ArrayList<>();
        List<String> reused = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String id : distinct) {
            if (currentIds.contains(id)) {
                current.add(id);
            } else if (earlierIds.contains(id)) {
                reused.add(id);
            } else {
                unknown.add(id);
            }
        }
        return new UsedDocuments(distinct, current, reused, unknown);
    }

    public boolean isEmpty() {
        return declared.isEmpty();
    }
}
