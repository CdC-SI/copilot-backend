package zas.admin.zec.backend.rag;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtre à état appliqué au flux de texte du LLM pour extraire et masquer la balise de fin de tour
 * {@code <turn_meta status="answer|followup|refusal" reuse="id1,id2"/>}.
 *
 * <p>Le texte est transmis au fil de l'eau ; seule une éventuelle fin de chunk pouvant être le début
 * de la balise est retenue jusqu'au chunk suivant. La balise (et tout ce qui suit dans le même bloc
 * jusqu'à sa fermeture) n'est jamais émise. Le parsing est tolérant : ordre des attributs, espaces,
 * guillemets simples ou doubles, fermeture {@code />}, {@code >} ou {@code </turn_meta>}.</p>
 *
 * <p>Non thread-safe : une instance par réponse, alimentée séquentiellement.</p>
 */
public final class TurnMetaStreamFilter {

    private static final String OPEN = "<turn_meta";
    private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern REUSE_ID = Pattern.compile("^[A-Za-z0-9_\\-:.#]{1,128}$");

    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder tag = new StringBuilder();
    private boolean inTag;
    private boolean tagClosed;
    private TurnMeta meta;

    /** Métadonnées de fin de tour extraites. */
    public record TurnMeta(TurnStatus status, List<String> reuseIds) {}

    /** @return le texte pouvant être émis immédiatement (éventuellement vide). */
    public String accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        if (inTag) {
            return consumeTag(chunk);
        }
        pending.append(chunk);
        int start = pending.indexOf(OPEN);
        if (start >= 0) {
            String before = pending.substring(0, start);
            String rest = pending.substring(start);
            pending.setLength(0);
            inTag = true;
            return before + consumeTag(rest);
        }
        // Retenir un suffixe susceptible d'être le début de "<turn_meta".
        int hold = partialOpenSuffix(pending);
        String emit = pending.substring(0, pending.length() - hold);
        pending.delete(0, pending.length() - hold);
        return emit;
    }

    /** À appeler en fin de flux : retourne le texte restant à émettre et finalise le parsing. */
    public String flush() {
        if (inTag && !tagClosed) {
            parse(tag.toString());
        }
        String rest = pending.toString();
        pending.setLength(0);
        return rest;
    }

    /** @return la balise extraite, ou {@link Optional#empty()} si absente. */
    public Optional<TurnMeta> meta() {
        return Optional.ofNullable(meta);
    }

    private String consumeTag(String chunk) {
        if (tagClosed) {
            // Texte après la balise : on le laisse passer (la balise devrait être en fin de réponse).
            return chunk;
        }
        tag.append(chunk);
        int end = closingIndex(tag);
        if (end < 0) {
            return "";
        }
        tagClosed = true;
        String after = tag.substring(end);
        parse(tag.substring(0, end));
        return after.stripLeading().isEmpty() ? "" : after;
    }

    private static int closingIndex(CharSequence s) {
        String str = s.toString();
        int explicit = str.indexOf("</turn_meta>");
        if (explicit >= 0) {
            return explicit + "</turn_meta>".length();
        }
        int gt = str.indexOf('>');
        return gt >= 0 ? gt + 1 : -1;
    }

    private void parse(String rawTag) {
        TurnStatus status = TurnStatus.UNKNOWN;
        List<String> reuse = new ArrayList<>();
        Matcher m = ATTRIBUTE.matcher(rawTag);
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            String value = m.group(2);
            if ("status".equals(name)) {
                status = TurnStatus.fromKey(value);
            } else if ("reuse".equals(name)) {
                Arrays.stream(value.split("[,\\s]+"))
                        .map(String::strip)
                        .filter(id -> REUSE_ID.matcher(id).matches())
                        .distinct()
                        .forEach(reuse::add);
            }
        }
        meta = new TurnMeta(status, List.copyOf(reuse));
    }

    private static int partialOpenSuffix(CharSequence s) {
        int max = Math.min(OPEN.length() - 1, s.length());
        for (int len = max; len > 0; len--) {
            boolean match = true;
            for (int i = 0; i < len; i++) {
                if (s.charAt(s.length() - len + i) != OPEN.charAt(i)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return len;
            }
        }
        return 0;
    }
}
