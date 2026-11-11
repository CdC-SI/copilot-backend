package zas.admin.zec.backend.rag;

import org.springframework.ai.document.Document;
import zas.admin.zec.backend.persistence.MetadataValues;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.tools.MetadataFormatter;
import zas.admin.zec.backend.tools.document.DocumentCollection;

import java.util.*;

/**
 * Représentation compacte d'un document consulté par l'agent pendant un tour (RAGTool ou
 * DocumentTool). Persistée dans {@code chat_history.consulted_sources}, elle sert à :
 * <ul>
 *   <li>réinjecter la liste des documents consultés dans le contexte des tours suivants
 *       ({@code <retrieved_sources>}), sans leur contenu ;</li>
 *   <li>reconstruire les sources affichées avec la réponse finale (y compris après des questions de suivi).</li>
 * </ul>
 *
 * @param id            identifiant exact interrogeable via DocumentTool (id {@code vector_store} ou {@code business_process})
 * @param collection    collection où réside {@code id} ({@link DocumentCollection#key()})
 * @param docType       type logique ({@code business_process} pour un processus ou sa description), sinon {@code null}
 * @param title         titre (ou nom du processus)
 * @param url           url (ou lien BPanda)
 * @param pageNumber    page(s)
 * @param subsection    section
 * @param version       version (ou {@code bpanda_version})
 * @param state         état (ou {@code process_state})
 * @param parentId      {@code doc_id} du document parent, ou {@code process_id}
 * @param chunkIndex    index du chunk dans le document parent
 * @param outgoingLinks liens sortants résolus hors-ligne
 */
public record ConsultedSource(
        String id,
        String collection,
        String docType,
        String title,
        String url,
        String pageNumber,
        String subsection,
        String version,
        String state,
        String parentId,
        String chunkIndex,
        List<Map<String, Object>> outgoingLinks) {

    public boolean isAttachment() {
        return MetadataFormatter.COLLECTION_ATTACHMENT.equals(collection);
    }

    public boolean isBusinessProcess() {
        return MetadataFormatter.DOC_TYPE_BUSINESS_PROCESS.equals(docType)
                || DocumentCollection.BUSINESS_PROCESS.key().equals(collection);
    }

    /**
     * Token de source affichable. Pour la documentation, l'état ({@code state}) est transmis dans le
     * champ version : le frontend le traduit (ex. {@code personal.uploads}) — comportement historique conservé.
     */
    public SourceToken toSourceToken() {
        if (isBusinessProcess()) {
            return SourceToken.fromBusinessProcess(id, title, url, version);
        }
        if (url != null && !url.isBlank()) {
            return SourceToken.fromURLWithDetails(id, url, pageNumber, subsection, state);
        }
        return SourceToken.fromFileWithDetails(id, title, pageNumber, subsection, state);
    }

    public static ConsultedSource from(Document document) {
        Map<String, Object> m = document.getMetadata();
        DocumentCollection collection = MetadataFormatter.collectionOf(document);
        if (collection == DocumentCollection.BUSINESS_PROCESS) {
            return new ConsultedSource(
                    document.getId(), collection.key(), MetadataFormatter.DOC_TYPE_BUSINESS_PROCESS,
                    MetadataValues.getString(m, "process_name"),
                    MetadataValues.getString(m, "bpanda_link"),
                    null, null,
                    MetadataValues.getString(m, "bpanda_version"),
                    MetadataValues.getString(m, "process_state"),
                    MetadataValues.getString(m, "process_id"),
                    null,
                    List.of());
        }
        String docType = MetadataValues.getString(m, MetadataFormatter.META_DOC_TYPE);
        boolean processDescription = MetadataFormatter.DOC_TYPE_BUSINESS_PROCESS.equals(docType);
        return new ConsultedSource(
                document.getId(), MetadataFormatter.collectionKey(document), docType,
                processDescription
                        ? firstNonBlank(MetadataValues.getString(m, "process_name"), MetadataValues.getString(m, "title"))
                        : MetadataValues.getString(m, "title"),
                processDescription
                        ? firstNonBlank(MetadataValues.getString(m, "bpanda_link"), MetadataValues.getString(m, "url"))
                        : MetadataValues.getString(m, "url"),
                MetadataValues.getString(m, "page_num"),
                MetadataValues.getString(m, "subsection"),
                MetadataValues.getString(m, "version"),
                MetadataValues.getString(m, "state"),
                processDescription ? MetadataValues.getString(m, "process_id") : MetadataValues.getString(m, "doc_id"),
                MetadataValues.getString(m, "chunk_index"),
                links(m.get("outgoing_links")));
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> links(Object raw) {
        if (!(raw instanceof Collection<?> c)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : c) {
            if (o instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return List.copyOf(result);
    }

    // ---- (dé)sérialisation jsonb ----

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "id", id);
        put(map, "collection", collection);
        put(map, "doc_type", docType);
        put(map, "title", title);
        put(map, "url", url);
        put(map, "page_num", pageNumber);
        put(map, "subsection", subsection);
        put(map, "version", version);
        put(map, "state", state);
        put(map, "parent_id", parentId);
        put(map, "chunk_index", chunkIndex);
        if (outgoingLinks != null && !outgoingLinks.isEmpty()) {
            map.put("outgoing_links", outgoingLinks);
        }
        return map;
    }

    public static ConsultedSource fromMap(Map<String, Object> map) {
        return new ConsultedSource(
                MetadataValues.getString(map, "id"),
                MetadataValues.getString(map, "collection", DocumentCollection.DOCUMENTATION.key()),
                MetadataValues.getString(map, "doc_type"),
                MetadataValues.getString(map, "title"),
                MetadataValues.getString(map, "url"),
                MetadataValues.getString(map, "page_num"),
                MetadataValues.getString(map, "subsection"),
                MetadataValues.getString(map, "version"),
                MetadataValues.getString(map, "state"),
                MetadataValues.getString(map, "parent_id"),
                MetadataValues.getString(map, "chunk_index"),
                links(map.get("outgoing_links")));
    }

    private static void put(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
