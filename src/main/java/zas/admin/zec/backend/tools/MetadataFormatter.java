package zas.admin.zec.backend.tools;

import org.springframework.ai.document.Document;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.tools.document.DocumentCollection;

import java.util.*;

/**
 * Mise en forme des documents exposés au LLM par les tools de recherche ({@link RAGTool},
 * {@link DocumentTool}) : contenu encadré par {@code <document>} suivi des métadonnées utiles
 * encadrées par {@code <metadata>} (JSON).
 *
 * <p>Seules les clés d'une liste blanche sont exposées (jamais {@code user_uuid},
 * {@code organizations}…), dans un ordre fixe, afin d'obtenir un rendu déterministe
 * (favorable au prefix caching) et de limiter les tokens.</p>
 */
public final class MetadataFormatter {

    /** Clé interne (non exposée telle quelle) indiquant la collection d'origine d'un {@link Document}. */
    public static final String META_COLLECTION = "_collection";

    /**
     * Collection des pièces jointes de la conversation (lues via {@code get_attachment_content}). Volontairement
     * absente de {@link DocumentCollection} : elle n'est pas interrogeable par {@code get_documents}.
     */
    public static final String COLLECTION_ATTACHMENT = "attachment";

    public static final String META_DOC_TYPE = "doc_type";
    public static final String DOC_TYPE_BUSINESS_PROCESS = "business_process";

    /** Clés exposées pour un chunk de documentation ({@code vector_store}). */
    public static final List<String> DOCUMENTATION_KEYS = List.of(
            "doc_id", "chunk_index", "title", "source", "url", "page_num", "subsection", "version", "state",
            META_DOC_TYPE, "process_id", "bpmn_id", "process_name", "lang", "outgoing_links");

    /** Clés exposées pour un processus métier ({@code business_process}). */
    public static final List<String> BUSINESS_PROCESS_KEYS = List.of(
            "bpmn_id", "process_id", "process_name", "process_state", "purpose", "bpanda_version",
            "valid_from", "valid_to", "bpanda_link", "n_nodes", "n_arcs", "lang");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private MetadataFormatter() {}

    public static DocumentCollection collectionOf(Document document) {
        Object raw = document.getMetadata().get(META_COLLECTION);
        return raw == null ? DocumentCollection.DOCUMENTATION : DocumentCollection.fromKey(String.valueOf(raw));
    }

    /** Clé de collection d'un document, y compris {@link #COLLECTION_ATTACHMENT} (hors {@link DocumentCollection}). */
    public static String collectionKey(Document document) {
        return COLLECTION_ATTACHMENT.equals(document.getMetadata().get(META_COLLECTION))
                ? COLLECTION_ATTACHMENT
                : collectionOf(document).key();
    }

    /** Métadonnées filtrées par liste blanche, dans l'ordre de la liste, sans valeurs nulles ou vides. */
    public static Map<String, Object> visibleMetadata(Document document) {
        var keys = collectionOf(document) == DocumentCollection.BUSINESS_PROCESS
                ? BUSINESS_PROCESS_KEYS
                : DOCUMENTATION_KEYS;
        Map<String, Object> source = document.getMetadata();
        Map<String, Object> visible = new LinkedHashMap<>();
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null || (value instanceof String s && s.isBlank())
                    || (value instanceof Collection<?> c && c.isEmpty())) {
                continue;
            }
            visible.put(key, value);
        }
        return visible;
    }

    public static String metadataJson(Document document) {
        return JSON.writeValueAsString(visibleMetadata(document));
    }

    /**
     * @param includeContent  inclure le texte du document
     * @param includeMetadata inclure le bloc {@code <metadata>}
     */
    public static String format(Document document, boolean includeContent, boolean includeMetadata) {
        String nl = System.lineSeparator();
        var sb = new StringBuilder();
        String header = "<document id=\"%s\" collection=\"%s\"".formatted(
                document.getId(), collectionKey(document));
        if (includeContent) {
            sb.append(header).append('>').append(nl)
                    .append(Objects.requireNonNullElse(document.getText(), "")).append(nl)
                    .append("</document>");
        } else {
            sb.append(header).append("/>");
        }
        if (includeMetadata) {
            sb.append(nl).append("<metadata>").append(metadataJson(document)).append("</metadata>");
        }
        return sb.toString();
    }
}
