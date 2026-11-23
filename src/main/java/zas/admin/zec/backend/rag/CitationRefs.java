package zas.admin.zec.backend.rag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Références de citation d'un tour (F2a) : chaque extrait lu par l'agent (document ou pièce jointe, avec son contenu)
 * reçoit un numéro court ({@code ref}), attribué dans l'ordre de lecture et stable pour un même identifiant pendant le
 * tour. L'agent cite {@code [ref]} ; {@link CitationStreamFilter} résout ensuite la référence vers l'identifiant.
 *
 * <p>Thread-safe : les tools d'une même étape peuvent s'exécuter en parallèle.</p>
 */
public final class CitationRefs {

    private final Map<String, Integer> refsById = new LinkedHashMap<>();
    private final Map<Integer, String> idsByRef = new LinkedHashMap<>();

    /** Référence de {@code id}, attribuée au premier appel. */
    public synchronized int refFor(String id) {
        return refsById.computeIfAbsent(id, key -> {
            int ref = refsById.size() + 1;
            idsByRef.put(ref, key);
            return ref;
        });
    }

    public synchronized Optional<String> idFor(int ref) {
        return Optional.ofNullable(idsByRef.get(ref));
    }

    public synchronized boolean isEmpty() {
        return idsByRef.isEmpty();
    }

    /** Table ref → identifiant, dans l'ordre d'attribution. */
    public synchronized Map<Integer, String> table() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(idsByRef));
    }
}
