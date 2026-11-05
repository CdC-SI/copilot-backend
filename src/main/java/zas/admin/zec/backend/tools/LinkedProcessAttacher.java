package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.config.properties.LinkedProcessProperties;
import zas.admin.zec.backend.tools.RAGTool.LinkedSource;
import zas.admin.zec.backend.tools.document.BusinessProcessLookup;
import zas.admin.zec.backend.tools.document.DocumentCollection;
import zas.admin.zec.backend.tools.document.DocumentLookup;
import zas.admin.zec.backend.tools.document.FilterOp;
import zas.admin.zec.backend.tools.document.JsonbFilterSqlBuilder;
import zas.admin.zec.backend.tools.document.MetadataFilterSpec;

import java.util.*;
import java.util.function.Function;

/**
 * Joint d'office au contexte de l'agent le contenu des processus métier liés, plutôt que de lui demander d'aller les
 * lire : le LLM néglige souvent un appel {@code get_documents} lorsque l'extrait d'un document lui semble suffire.
 * <ul>
 *   <li>{@link #forSearch} : processus liés aux documents retournés par la recherche (version en vigueur) ;</li>
 *   <li>{@link #forFollowUp} : processus lus au tour précédent, relus par id (même version) lorsque ce tour
 *       était une question de suivi, pour que la réponse de l'utilisateur soit appliquée au processus.</li>
 * </ul>
 * Les processus joints sont rendus dans un bloc {@code <linked_processes>}, déclaré dans le prompt système comme
 * ajouté par le système et sans consigne. Réservé aux utilisateurs ayant accès aux documents internes.
 */
@Slf4j
@Component
public class LinkedProcessAttacher {

    /** Processus joint, avec l'id du document qui y renvoie ({@code null} pour un processus en cours). */
    public record Attached(String from, Document document) {}

    /**
     * @param attached processus joints, dans l'ordre de pertinence
     * @param notice   notice de {@link BusinessProcessLookup} (autres versions, langue), ou {@code null}
     */
    public record Attachment(List<Attached> attached, String notice) {
        static final Attachment NONE = new Attachment(List.of(), null);

        public List<Document> documents() {
            return attached.stream().map(Attached::document).toList();
        }
    }

    private final BusinessProcessLookup lookup;
    private final LinkedProcessProperties properties;

    public LinkedProcessAttacher(BusinessProcessLookup lookup, LinkedProcessProperties properties) {
        this.lookup = lookup;
        this.properties = properties;
    }

    /** Premiers processus parmi les cibles de {@code <linked_sources>} (ordre de pertinence des documents). */
    public Attachment forSearch(List<LinkedSource> targets, String userId, boolean hasInternalAccess, String language) {
        if (!properties.enabled() || !hasInternalAccess) {
            return Attachment.NONE;
        }
        Map<String, String> fromByProcess = new LinkedHashMap<>();
        for (LinkedSource target : targets) {
            if (target.collection() == DocumentCollection.BUSINESS_PROCESS && fromByProcess.size() < properties.maxProcesses()) {
                fromByProcess.putIfAbsent(target.value(), target.from());
            }
        }
        if (fromByProcess.isEmpty()) {
            return Attachment.NONE;
        }
        var result = find("process_id", fromByProcess.keySet(), userId, language);
        List<Document> ordered = result.documents().stream()
                .sorted(Comparator.comparingInt(d -> indexOf(fromByProcess.keySet(), d.getMetadata().get("process_id"))))
                .toList();
        return withinBudget(ordered, d -> fromByProcess.get(String.valueOf(d.getMetadata().get("process_id"))),
                result.notice());
    }

    /** Processus du tour précédent (ids {@code business_process}), relus dans la version alors consultée. */
    public Attachment forFollowUp(List<String> processIds, String userId, boolean hasInternalAccess, String language) {
        if (!properties.enabled() || !hasInternalAccess || processIds.isEmpty()) {
            return Attachment.NONE;
        }
        List<String> ids = processIds.stream().distinct().limit(properties.maxProcesses()).toList();
        var result = find(JsonbFilterSqlBuilder.KEY_ID, ids, userId, language);
        return withinBudget(result.documents(), d -> null, result.notice());
    }

    private DocumentLookup.LookupResult find(String key, Collection<String> values, String userId, String language) {
        var filter = new MetadataFilterSpec(key, FilterOp.IN, null, List.copyOf(values));
        try {
            return lookup.find(new DocumentLookup.LookupRequest(
                    List.of(filter), properties.maxProcesses(), userId, true, language, true));
        } catch (RuntimeException e) {
            // Un processus joint est un complément : un échec ne doit pas faire échouer la recherche.
            log.warn("Processus liés non joints ({}={}) : {}", key, values, e.getMessage());
            return new DocumentLookup.LookupResult(List.of(), false, null);
        }
    }

    private Attachment withinBudget(List<Document> documents, Function<Document, String> from,
                                    String notice) {
        List<Attached> attached = new ArrayList<>();
        int chars = 0;
        for (Document document : documents) {
            int size = Objects.requireNonNullElse(document.getText(), "").length();
            if (chars + size > properties.maxChars()) {
                log.debug("Processus {} non joint : {} caractères au-delà de la limite de {}",
                        document.getMetadata().get("process_id"), size, properties.maxChars());
                continue;
            }
            chars += size;
            attached.add(new Attached(from.apply(document), document));
        }
        return attached.isEmpty() ? Attachment.NONE : new Attachment(List.copyOf(attached), notice);
    }

    private static int indexOf(Collection<String> values, Object value) {
        int i = 0;
        for (String v : values) {
            if (v.equals(String.valueOf(value))) {
                return i;
            }
            i++;
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Bloc {@code <linked_processes>} : chaque processus dans le format habituel des documents ({@code <document>}
     * suivi de {@code <metadata>}), encadré par {@code <linked_process from="...">} ; sans consigne. Chaîne vide
     * sans processus.
     */
    public static String format(Attachment attachment) {
        if (attachment.attached().isEmpty()) {
            return "";
        }
        String nl = System.lineSeparator();
        var sb = new StringBuilder("<linked_processes>").append(nl);
        if (attachment.notice() != null) {
            sb.append("<notice>").append(attachment.notice()).append("</notice>").append(nl);
        }
        for (Attached a : attachment.attached()) {
            sb.append(a.from() != null ? "<linked_process from=\"" + a.from() + "\">" : "<linked_process>").append(nl)
                    .append(MetadataFormatter.format(a.document(), true, true)).append(nl)
                    .append("</linked_process>").append(nl);
        }
        return sb.append("</linked_processes>").toString();
    }
}
