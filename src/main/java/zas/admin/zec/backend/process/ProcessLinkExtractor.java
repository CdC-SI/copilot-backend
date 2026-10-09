package zas.admin.zec.backend.process;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrait des {@code outgoing_links} d'un document les références vers des processus BPMN.
 *
 * <p>Deux formats sont acceptés :</p>
 * <ul>
 *   <li>format actuel : objet {@code {text, url, page}} dont l'URL BPanda porte
 *       {@code processId=ID-<32 hex>}, qui vaut le {@code bpanda_id} du processus ;</li>
 *   <li>format cible : chaîne {@code "process:<nom du processus>"}.</li>
 * </ul>
 * Seule cette classe dépend de la forme des métadonnées : un changement de schéma s'arrête ici.
 */
public final class ProcessLinkExtractor {

    static final String OUTGOING_LINKS = "outgoing_links";
    private static final String URL = "url";
    private static final String PROCESS_PREFIX = "process:";
    private static final Pattern PROCESS_ID = Pattern.compile("processId=(ID-[0-9a-fA-F]{32})");

    private ProcessLinkExtractor() {}

    /**
     * @param bpandaIds identifiants BPanda trouvés dans les URL
     * @param names     noms de processus trouvés au format {@code process:<nom>}
     */
    public record ProcessRefs(Set<String> bpandaIds, Set<String> names) {
        public boolean isEmpty() {
            return bpandaIds.isEmpty() && names.isEmpty();
        }
    }

    public static ProcessRefs extract(Map<String, ?> metadata) {
        Set<String> bpandaIds = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        if (metadata != null && metadata.get(OUTGOING_LINKS) instanceof Collection<?> links) {
            for (Object link : links) {
                switch (link) {
                    case Map<?, ?> map when map.get(URL) instanceof String url -> addProcessId(url, bpandaIds);
                    case String s when s.startsWith(PROCESS_PREFIX) -> {
                        String name = s.substring(PROCESS_PREFIX.length()).strip();
                        if (!name.isEmpty()) {
                            names.add(name);
                        }
                    }
                    case String s -> addProcessId(s, bpandaIds);
                    case null, default -> { /* lien sans référence de processus */ }
                }
            }
        }
        return new ProcessRefs(bpandaIds, names);
    }

    private static void addProcessId(String url, Set<String> bpandaIds) {
        Matcher matcher = PROCESS_ID.matcher(url);
        if (matcher.find()) {
            bpandaIds.add(matcher.group(1));
        }
    }
}
