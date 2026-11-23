package zas.admin.zec.backend.rag;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

/**
 * Filtre à état appliqué au texte de la réponse (après {@link TurnMetaStreamFilter}) pour résoudre les citations
 * (F2a). L'agent cite les extraits lus pendant le tour par leur référence ({@code [3]}, {@code [3][5]},
 * {@code [3, 5]} ; {@code [^3]} est aussi accepté) ; chaque référence est remplacée par le marqueur canonique
 * {@code [^n]}, où {@code n} est le numéro affiché, attribué dans l'ordre de première citation. Deux extraits qui
 * correspondent à la même source à l'écran ({@link zas.admin.zec.backend.rag.token.SourceToken#displayKey()})
 * partagent le même numéro.
 *
 * <ul>
 *   <li>Une référence inconnue est retirée (journalisée) ; si le tour n'a aucune référence (aucun extrait lu), le texte
 *       est transmis tel quel.</li>
 *   <li>Le code n'est pas modifié : code en ligne ({@code `…`}) et blocs ({@code ```…```}).</li>
 *   <li>Un marqueur coupé entre deux morceaux du flux est retenu jusqu'au morceau suivant.</li>
 * </ul>
 *
 * <p>Non thread-safe : une instance par réponse, alimentée séquentiellement.</p>
 */
@Slf4j
public final class CitationStreamFilter {

    /** Au-delà, un {@code [} retenu n'est plus considéré comme un début de marqueur. */
    private static final int MAX_MARKER_LENGTH = 40;
    private static final int MAX_REF_DIGITS = 3;
    private static final int NOT_A_MARKER = -1;
    private static final int INCOMPLETE = -2;
    private static final Pattern CANONICAL_MARKER = Pattern.compile("[ \\t]*\\[\\^\\d{1,3}]");

    /**
     * Source citée.
     *
     * @param number numéro affiché
     * @param ref    référence du premier extrait cité pour ce numéro
     * @param source extrait cité
     */
    public record Citation(int number, int ref, ConsultedSource source) {}

    private final IntFunction<Optional<ConsultedSource>> resolver;
    private final CitationRefs refs;
    private final StringBuilder pending = new StringBuilder();
    private final Map<String, Citation> byDisplayKey = new LinkedHashMap<>();
    private final List<Citation> fresh = new ArrayList<>();
    private boolean inFence;
    private int inlineTicks;
    private int unknownRefs;
    /** Numéro du marqueur qui termine le texte émis (0 sinon) : un marqueur identique accolé n'est pas répété. */
    private int trailingMarker;

    /**
     * @param refs     références du tour
     * @param resolver extrait correspondant à une référence ({@link Optional#empty()} si inconnue)
     */
    public CitationStreamFilter(CitationRefs refs, IntFunction<Optional<ConsultedSource>> resolver) {
        this.refs = refs;
        this.resolver = resolver;
    }

    /** @return le texte pouvant être émis immédiatement (éventuellement vide). */
    public String accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        pending.append(chunk);
        return process(false);
    }

    /** À appeler en fin de flux : retourne le texte restant. */
    public String flush() {
        return process(true);
    }

    /** Sources citées pour la première fois depuis le dernier appel, dans l'ordre des numéros. */
    public List<Citation> drainNewCitations() {
        List<Citation> drained = List.copyOf(fresh);
        fresh.clear();
        return drained;
    }

    /** Toutes les sources citées, dans l'ordre des numéros. */
    public List<Citation> citations() {
        return List.copyOf(byDisplayKey.values());
    }

    /** Nombre de références inconnues retirées. */
    public int unknownRefs() {
        return unknownRefs;
    }

    /** Texte sans marqueurs de citation {@code [^n]} (historique et résumé envoyés au LLM, titre). */
    public static String stripMarkers(String text) {
        return text == null ? null : CANONICAL_MARKER.matcher(text).replaceAll("");
    }

    private String process(boolean end) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = pending.length();
        while (i < n) {
            char c = pending.charAt(i);
            if (c == '`') {
                int run = i;
                while (run < n && pending.charAt(run) == '`') {
                    run++;
                }
                if (run == n && !end) {
                    break;
                }
                toggleCode(run - i);
                out.append(pending, i, run);
                trailingMarker = 0;
                i = run;
                continue;
            }
            if (c == '\n') {
                // Le code en ligne ne franchit pas une fin de ligne (backtick isolé).
                inlineTicks = 0;
            }
            if (c != '[' || inFence || inlineTicks > 0 || refs.isEmpty()) {
                out.append(c);
                trailingMarker = 0;
                i++;
                continue;
            }
            List<Integer> parsed = new ArrayList<>();
            int markerEnd = markerEnd(pending, i, parsed);
            if (markerEnd == INCOMPLETE) {
                if (!end && n - i <= MAX_MARKER_LENGTH) {
                    break;
                }
                markerEnd = NOT_A_MARKER;
            }
            if (markerEnd == NOT_A_MARKER) {
                out.append(c);
                trailingMarker = 0;
                i++;
                continue;
            }
            out.append(render(parsed));
            i = markerEnd;
        }
        pending.delete(0, i);
        if (end && !pending.isEmpty()) {
            out.append(pending);
            pending.setLength(0);
        }
        return out.toString();
    }

    private void toggleCode(int ticks) {
        if (inFence) {
            inFence = ticks < 3;
        } else if (inlineTicks > 0) {
            if (ticks == inlineTicks) {
                inlineTicks = 0;
            }
        } else if (ticks >= 3) {
            inFence = true;
        } else {
            inlineTicks = ticks;
        }
    }

    /**
     * Fin (exclue) du marqueur commençant à {@code start} ({@code [}), {@link #INCOMPLETE} si le texte disponible ne
     * permet pas de trancher, ou {@link #NOT_A_MARKER}. Les références lues sont ajoutées à {@code parsed}.
     */
    static int markerEnd(CharSequence s, int start, List<Integer> parsed) {
        int n = s.length();
        int i = start + 1;
        if (i < n && s.charAt(i) == '^') {
            i++;
        }
        while (true) {
            int digits = i;
            while (i < n && Character.isDigit(s.charAt(i)) && i - digits <= MAX_REF_DIGITS) {
                i++;
            }
            if (i == n) {
                return i - digits > MAX_REF_DIGITS ? NOT_A_MARKER : INCOMPLETE;
            }
            if (i == digits || i - digits > MAX_REF_DIGITS) {
                return NOT_A_MARKER;
            }
            parsed.add(Integer.parseInt(s.subSequence(digits, i).toString()));
            i = skipSpaces(s, i);
            if (i == n) {
                return INCOMPLETE;
            }
            char c = s.charAt(i);
            if (c == ']') {
                // [3][5] : marqueurs consécutifs traités un par un.
                return i + 1;
            }
            if (c != ',' && c != ';') {
                return NOT_A_MARKER;
            }
            i = skipSpaces(s, i + 1);
            if (i < n && s.charAt(i) == '^') {
                i++;
            }
        }
    }

    private static int skipSpaces(CharSequence s, int i) {
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** Marqueurs canoniques des références lues ; un numéro qui suit immédiatement le même numéro n'est pas répété. */
    private String render(List<Integer> parsed) {
        StringBuilder sb = new StringBuilder();
        Set<Integer> numbers = new LinkedHashSet<>();
        for (int ref : parsed) {
            Optional<ConsultedSource> source = resolver.apply(ref);
            if (source.isEmpty()) {
                unknownRefs++;
                log.warn("Citation [{}] inconnue retirée de la réponse", ref);
                continue;
            }
            numbers.add(numberFor(ref, source.get()));
        }
        for (int number : numbers) {
            if (number != trailingMarker) {
                sb.append("[^").append(number).append(']');
                trailingMarker = number;
            }
        }
        return sb.toString();
    }

    private int numberFor(int ref, ConsultedSource source) {
        String key = source.toSourceToken().displayKey();
        Citation existing = byDisplayKey.get(key);
        if (existing != null) {
            return existing.number();
        }
        Citation citation = new Citation(byDisplayKey.size() + 1, ref, source);
        byDisplayKey.put(key, citation);
        fresh.add(citation);
        return citation.number();
    }
}
