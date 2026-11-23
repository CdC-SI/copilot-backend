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
 *   <li>Le code n'est pas modifié : code en ligne ({@code `…`}) et blocs délimités en tête de ligne par au moins trois
 *       {@code `} ou {@code ~}, fermés par une ligne du même caractère et au moins aussi longue.</li>
 *   <li>Un libellé numérique de lien ou d'image Markdown ({@code [2](…)}, {@code ![2](…)}) ou de définition de lien
 *       ({@code [2]: …} en tête de ligne) n'est pas une citation.</li>
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
    /** Caractère du bloc de code ouvert ({@code `} ou {@code ~}), {@code 0} hors bloc. */
    private char fenceChar;
    private int fenceLength;
    private int inlineTicks;
    /** Début de ligne : seuls des espaces (au plus 3) ont été émis depuis la dernière fin de ligne. */
    private boolean lineStart = true;
    private int lineIndent;
    private char lastChar;
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

    /**
     * Texte sans marqueurs de citation {@code [^n]} (historique et résumé envoyés au LLM, titre). Le code (blocs et
     * code en ligne) est conservé tel quel, comme dans le flux.
     */
    public static String stripMarkers(String text) {
        if (text == null || !text.contains("[^")) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        char openFence = 0;
        int openLength = 0;
        int pos = 0;
        while (pos < text.length()) {
            int eol = text.indexOf('\n', pos);
            int next = eol < 0 ? text.length() : eol + 1;
            String line = text.substring(pos, next);
            int[] fence = fenceAt(line);
            if (openFence != 0) {
                out.append(line);
                if (fence != null && line.charAt(fence[0]) == openFence && fence[1] - fence[0] >= openLength
                        && line.substring(fence[1]).isBlank()) {
                    openFence = 0;
                }
            } else if (fence != null) {
                openFence = line.charAt(fence[0]);
                openLength = fence[1] - fence[0];
                out.append(line);
            } else {
                out.append(stripOutsideInlineCode(line));
            }
            pos = next;
        }
        return out.toString();
    }

    private String process(boolean end) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = pending.length();
        while (i < n) {
            char c = pending.charAt(i);
            if (lineStart && (c == '`' || c == '~') && (fenceChar == 0 || c == fenceChar)) {
                int run = runEnd(pending, i, c);
                if (run == n && !end) {
                    break;
                }
                int length = run - i;
                if (fenceChar == 0 && length >= 3) {
                    fenceChar = c;
                    fenceLength = length;
                    inlineTicks = 0;
                    i = emit(out, i, run);
                    continue;
                }
                if (fenceChar != 0 && length >= fenceLength) {
                    // Fermeture seulement si le reste de la ligne est vide.
                    int eol = indexOf(pending, '\n', run);
                    if (eol < 0 && !end) {
                        break;
                    }
                    int stop = eol < 0 ? n : eol;
                    if (pending.subSequence(run, stop).toString().isBlank()) {
                        fenceChar = 0;
                    }
                    i = emit(out, i, stop);
                    continue;
                }
            }
            if (fenceChar != 0) {
                i = emit(out, i, i + 1);
                continue;
            }
            if (c == '`') {
                int run = runEnd(pending, i, c);
                if (run == n && !end) {
                    break;
                }
                toggleInlineCode(run - i);
                i = emit(out, i, run);
                continue;
            }
            if (c != '[' || inlineTicks > 0 || lastChar == '!' || refs.isEmpty()) {
                i = emit(out, i, i + 1);
                continue;
            }
            List<Integer> parsed = new ArrayList<>();
            int markerEnd = markerEnd(pending, i, parsed);
            if (markerEnd >= 0) {
                markerEnd = linkLabelCheck(markerEnd, n, end);
            }
            if (markerEnd == INCOMPLETE) {
                if (!end && n - i <= MAX_MARKER_LENGTH) {
                    break;
                }
                markerEnd = NOT_A_MARKER;
            }
            if (markerEnd == NOT_A_MARKER) {
                i = emit(out, i, i + 1);
                continue;
            }
            String rendered = render(parsed);
            if (!rendered.isEmpty()) {
                out.append(rendered);
                lineStart = false;
                lastChar = ']';
            }
            i = markerEnd;
        }
        pending.delete(0, i);
        if (end && !pending.isEmpty()) {
            emit(out, 0, pending.length());
            pending.setLength(0);
        }
        return out.toString();
    }

    /**
     * Un libellé numérique suivi de {@code (} (lien ou image Markdown) ou, en début de ligne, de {@code :}
     * (définition de lien) n'est pas une citation. Retourne {@code markerEnd}, {@link #NOT_A_MARKER}, ou
     * {@link #INCOMPLETE} si le caractère suivant n'est pas encore reçu.
     */
    private int linkLabelCheck(int markerEnd, int n, boolean end) {
        if (markerEnd == n) {
            return end ? markerEnd : INCOMPLETE;
        }
        char next = pending.charAt(markerEnd);
        if (next == '(' || (next == ':' && lineStart)) {
            return NOT_A_MARKER;
        }
        return markerEnd;
    }

    /** Copie {@code pending[from, to)} dans {@code out} en suivant le début de ligne ; retourne {@code to}. */
    private int emit(StringBuilder out, int from, int to) {
        for (int k = from; k < to; k++) {
            char c = pending.charAt(k);
            out.append(c);
            if (c == '\n') {
                lineStart = true;
                lineIndent = 0;
                // Le code en ligne ne franchit pas une fin de ligne (backtick isolé).
                inlineTicks = 0;
            } else if (c == ' ' && lineStart && lineIndent < 3) {
                lineIndent++;
            } else {
                lineStart = false;
            }
            lastChar = c;
        }
        if (to > from) {
            trailingMarker = 0;
        }
        return to;
    }

    private void toggleInlineCode(int ticks) {
        if (inlineTicks == 0) {
            inlineTicks = ticks;
        } else if (ticks == inlineTicks) {
            inlineTicks = 0;
        }
    }

    private static int runEnd(CharSequence s, int start, char c) {
        int i = start;
        while (i < s.length() && s.charAt(i) == c) {
            i++;
        }
        return i;
    }

    private static int indexOf(CharSequence s, char c, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    /** Clôture de bloc de code en tête de ligne (au plus 3 espaces, puis au moins 3 {@code `} ou {@code ~}) : {@code [début, fin)}. */
    private static int[] fenceAt(String line) {
        int start = 0;
        while (start < line.length() && start < 3 && line.charAt(start) == ' ') {
            start++;
        }
        if (start == line.length() || (line.charAt(start) != '`' && line.charAt(start) != '~')) {
            return null;
        }
        int end = runEnd(line, start, line.charAt(start));
        return end - start >= 3 ? new int[]{start, end} : null;
    }

    /** Retire les marqueurs d'une ligne hors bloc, sauf dans les segments de code en ligne. */
    private static String stripOutsideInlineCode(String line) {
        StringBuilder out = new StringBuilder(line.length());
        int plainStart = 0;
        int i = 0;
        while (i < line.length()) {
            if (line.charAt(i) != '`') {
                i++;
                continue;
            }
            int run = runEnd(line, i, '`');
            int close = closingRun(line, run, run - i);
            if (close < 0) {
                i = run;
                continue;
            }
            out.append(CANONICAL_MARKER.matcher(line.substring(plainStart, i)).replaceAll(""));
            out.append(line, i, close);
            i = close;
            plainStart = close;
        }
        out.append(CANONICAL_MARKER.matcher(line.substring(plainStart)).replaceAll(""));
        return out.toString();
    }

    /** Fin de la première suite d'exactement {@code length} backticks à partir de {@code from}, ou -1. */
    private static int closingRun(String line, int from, int length) {
        int i = from;
        while (i < line.length()) {
            if (line.charAt(i) != '`') {
                i++;
                continue;
            }
            int run = runEnd(line, i, '`');
            if (run - i == length) {
                return run;
            }
            i = run;
        }
        return -1;
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
