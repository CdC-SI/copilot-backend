package zas.admin.zec.backend.actions.converse;

import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.tools.document.DocumentCollection;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Rendu compact des documents consultés lors d'un tour, réinjecté au début du message utilisateur
 * suivant sous la forme d'un bloc {@code <retrieved_sources turn="n">}.
 *
 * <p>Le bloc ne contient que des identifiants et métadonnées (pas de contenu) : l'agent peut relire un
 * document précis via {@code get_documents} (clé {@code id}) au lieu de relancer une recherche.
 * Le rendu est déterministe (ordre de consultation, pas d'horodatage) pour rester stable d'un tour à
 * l'autre et préserver le prefix caching.</p>
 */
final class RetrievedSourcesRenderer {

    private static final Pattern UUID_LIKE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private RetrievedSourcesRenderer() {}

    /** Sources consultées lors du tour porté par ce message assistant (trace, ou sources affichées pour l'historique). */
    static List<ConsultedSource> consultedSources(Message assistantMessage) {
        TurnTrace trace = assistantMessage.turnTrace();
        if (trace != null) {
            return trace.sources();
        }
        // Historique antérieur à la trace : on se rabat sur les sources affichées, si elles ont un id exploitable.
        if (assistantMessage.sources() == null) {
            return List.of();
        }
        return assistantMessage.sources().stream()
                .filter(s -> s.documentId() != null && UUID_LIKE.matcher(s.documentId()).matches())
                .map(RetrievedSourcesRenderer::fromLegacy)
                .toList();
    }

    static String render(int turn, List<ConsultedSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("<retrieved_sources turn=\"").append(turn).append("\">\n");
        Set<String> seen = new HashSet<>();
        for (ConsultedSource s : sources) {
            if (s.id() == null || !seen.add(s.id())) {
                continue;
            }
            sb.append(line(s)).append('\n');
        }
        return sb.append("</retrieved_sources>").toString();
    }

    private static String line(ConsultedSource s) {
        List<String> parts = new ArrayList<>();
        parts.add("id=" + s.id());
        parts.add("collection=" + (s.collection() != null ? s.collection() : DocumentCollection.DOCUMENTATION.key()));
        if (s.isBusinessProcess()) {
            parts.add("type=business_process");
        }
        if (s.isAttachment()) {
            // Pièce jointe : relue via get_attachment_content (nom du fichier), pas via get_documents.
            add(parts, "filename", quote(s.title()));
            return "- " + String.join("; ", parts);
        }
        add(parts, "title", quote(s.title()));
        add(parts, s.isBusinessProcess() ? "process_id" : "doc_id", s.parentId());
        add(parts, "chunk_index", s.chunkIndex());
        add(parts, "page", s.pageNumber());
        add(parts, "section", quote(s.subsection()));
        add(parts, "version", s.version());
        add(parts, "state", s.state());
        String links = links(s.outgoingLinks());
        if (!links.isEmpty()) {
            parts.add("outgoing_links=[" + links + "]");
        }
        return "- " + String.join("; ", parts);
    }

    private static String links(List<Map<String, Object>> links) {
        if (links == null || links.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Map<String, Object> link : links) {
            String type = str(link.get("type"));
            String processId = str(link.get("process_id"));
            String target = str(link.get("target_doc_id"));
            String title = str(link.get("title"));
            String url = str(link.get("url"));
            String ref;
            if (!processId.isEmpty()) {
                ref = "process_id=" + processId;
            } else if (!target.isEmpty()) {
                ref = "doc_id=" + target;
            } else {
                ref = "unresolved" + (url.isEmpty() ? "" : " url=" + url);
            }
            joiner.add((type.isEmpty() ? "link" : type) + ":" + (title.isEmpty() ? "" : quote(title) + " ") + ref);
        }
        return joiner.toString();
    }

    static ConsultedSource fromLegacy(Source source) {
        boolean url = source.type() == SourceType.URL;
        boolean process = source.type() == SourceType.BUSINESS_PROCESS;
        return new ConsultedSource(
                source.documentId(),
                DocumentCollection.DOCUMENTATION.key(),
                process ? "business_process" : null,
                process ? source.title() : (url ? null : source.link()),
                url || process ? source.link() : null,
                source.pageNumber(),
                source.subsection(),
                process ? source.version() : null,
                process ? null : source.version(),
                null, null, List.of());
    }

    private static void add(List<String> parts, String key, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(key + "=" + value);
        }
    }

    private static String quote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "\"" + value.replace('"', '\'').replace('\n', ' ').strip() + "\"";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).strip();
    }
}
