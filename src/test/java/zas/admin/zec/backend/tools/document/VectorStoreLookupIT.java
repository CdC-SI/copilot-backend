package zas.admin.zec.backend.tools.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import zas.admin.zec.backend.tools.MetadataFormatter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link VectorStoreLookup} sur une vraie base (jsonb, index V17) : navigation par doc_id / chunk_index,
 * règles d'accès identiques à RAGTool (documents internes, personnels) et robustesse des filtres.
 */
class VectorStoreLookupIT extends AbstractDocumentLookupIT {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    // Chunks synthétiques : id = 00000000-0000-0000-0000-0000000000xx
    private static final String D1_C0 = id(10);
    private static final String D1_C1 = id(11);
    private static final String D1_C2 = id(12);
    private static final String D1_C10 = id(13);
    private static final String INTERNAL = id(20);
    private static final String INTERNAL_SCALAR = id(21);
    private static final String NO_ORGANIZATIONS = id(22);
    private static final String PERSONAL_A = id(23);
    private static final String PROCESS_DESCRIPTION = id(24);

    @Autowired
    private VectorStoreLookup lookup;

    private static String id(int n) {
        return "00000000-0000-0000-0000-0000000000%02d".formatted(n);
    }

    private static Map<String, Object> publicChunk(String docId, Object chunkIndex) {
        Map<String, Object> m = new HashMap<>();
        m.put("doc_id", docId);
        m.put("chunk_index", chunkIndex);
        m.put("title", "Document " + docId);
        m.put("source", "it_source");
        m.put("user_uuid", "");
        m.put("organizations", List.of("OFAS"));
        return m;
    }

    private void insert(String id, String content, Map<String, Object> metadata) {
        jdbcTemplate.update("INSERT INTO vector_store (id, content, metadata) VALUES (?::uuid, ?, ?::jsonb)",
                id, content, json(metadata));
    }

    @BeforeEach
    void data() {
        var c0 = publicChunk("IT-D1", 0);
        c0.put("outgoing_links", List.of(
                Map.of("type", "process", "title", "Processus IT", "process_id", "IT-PROC-A"),
                Map.of("type", "document", "title", "Directive IT", "target_doc_id", "IT-D2"),
                Map.of("type", "external", "title", "Site externe", "url", "https://example.org/x")));
        insert(D1_C0, "D1 chunk 0", c0);
        insert(D1_C1, "D1 chunk 1", publicChunk("IT-D1", 1));
        insert(D1_C2, "D1 chunk 2", publicChunk("IT-D1", 2));
        insert(D1_C10, "D1 chunk 10", publicChunk("IT-D1", 10));

        var internal = publicChunk("IT-D2", "0");                 // chunk_index stocké en chaîne
        internal.put("organizations", List.of("ZAS"));
        insert(INTERNAL, "interne", internal);

        var internalScalar = publicChunk("IT-D3", 0);
        internalScalar.put("organizations", "ZAS");                 // valeur scalaire
        insert(INTERNAL_SCALAR, "interne scalaire", internalScalar);

        var noOrganizations = publicChunk("IT-D4", 0);
        noOrganizations.remove("organizations");
        insert(NO_ORGANIZATIONS, "sans organizations", noOrganizations);

        var personal = publicChunk("IT-D5", 0);
        personal.put("user_uuid", USER_A);
        insert(PERSONAL_A, "personnel", personal);

        var description = publicChunk("IT-BPMN-A", 0);
        description.put("doc_type", MetadataFormatter.DOC_TYPE_BUSINESS_PROCESS);
        description.put("process_id", "IT-PROC-A");
        description.put("bpmn_id", "IT-BPMN-A");
        description.put("organizations", List.of("ZAS"));
        insert(PROCESS_DESCRIPTION, "description du processus A", description);
    }

    @Test
    void readsNeighbourChunksInChunkOrder() {
        var result = lookup.find(request(List.of(eq("doc_id", "IT-D1"), in("chunk_index", "10", "1")), USER_A, false, "fr"));

        assertEquals(List.of(D1_C1, D1_C10), ids(result));
        assertFalse(result.truncated());
    }

    @Test
    void chunkIndexRangeIsNumericNotLexicographic() {
        var result = lookup.find(request(List.of(eq("doc_id", "IT-D1"), op("chunk_index", FilterOp.GTE, "2")), USER_A, false, "fr"));

        assertEquals(List.of(D1_C2, D1_C10), ids(result));
    }

    @Test
    void chunkIndexStoredAsStringIsMatchedNumerically() {
        var result = lookup.find(request(List.of(eq("doc_id", "IT-D2"), eq("chunk_index", "0")), USER_A, true, "fr"));

        assertEquals(List.of(INTERNAL), ids(result));
    }

    @Test
    void limitTruncatesAndFlagsMoreResults() {
        var result = lookup.find(new DocumentLookup.LookupRequest(List.of(eq("doc_id", "IT-D1")), 2, USER_A, false, "fr", true));

        assertEquals(List.of(D1_C0, D1_C1), ids(result));
        assertTrue(result.truncated());
    }

    @Test
    void internalDocumentsRequireInternalAccess() {
        assertTrue(lookup.find(request(List.of(eq("doc_id", "IT-D2")), USER_A, false, "fr")).documents().isEmpty());
        assertTrue(lookup.find(request(List.of(eq("doc_id", "IT-D3")), USER_A, false, "fr")).documents().isEmpty());
        assertEquals(List.of(INTERNAL), ids(lookup.find(request(List.of(eq("doc_id", "IT-D2")), USER_A, true, "fr"))));
        assertEquals(List.of(INTERNAL_SCALAR), ids(lookup.find(request(List.of(eq("doc_id", "IT-D3")), USER_A, true, "fr"))));
    }

    @Test
    void documentsWithoutOrganizationsAreHiddenFromExternalUsersOnly() {
        // Règle historique de RAGTool, réutilisée telle quelle.
        assertTrue(lookup.find(request(List.of(eq("doc_id", "IT-D4")), USER_A, false, "fr")).documents().isEmpty());
        assertEquals(List.of(NO_ORGANIZATIONS), ids(lookup.find(request(List.of(eq("doc_id", "IT-D4")), USER_A, true, "fr"))));
    }

    @Test
    void personalDocumentsAreVisibleToTheirOwnerOnly() {
        assertEquals(List.of(PERSONAL_A), ids(lookup.find(request(List.of(eq("doc_id", "IT-D5")), USER_A, true, "fr"))));
        assertTrue(lookup.find(request(List.of(eq("doc_id", "IT-D5")), USER_B, true, "fr")).documents().isEmpty());
    }

    @Test
    void findsById() {
        assertEquals(List.of(D1_C1), ids(lookup.find(request(List.of(eq("id", D1_C1)), USER_A, false, "fr"))));
    }

    @Test
    void metadataOnlyKeepsNativeJsonTypesWithoutContent() {
        var result = lookup.find(new DocumentLookup.LookupRequest(
                List.of(eq("doc_id", "IT-D1"), eq("chunk_index", "0")), 5, USER_A, false, "fr", false));

        var document = result.documents().getFirst();
        assertEquals("", document.getText());
        assertEquals(0, document.getMetadata().get("chunk_index"));
        assertInstanceOf(List.class, document.getMetadata().get("outgoing_links"));
        assertEquals(DocumentCollection.DOCUMENTATION, MetadataFormatter.collectionOf(document));
        assertFalse(MetadataFormatter.metadataJson(document).contains("user_uuid"));
        assertFalse(MetadataFormatter.metadataJson(document).contains("organizations"));
    }

    @Test
    void findsProcessDescriptionsByDocType() {
        var result = lookup.find(request(List.of(eq("doc_type", "business_process"), eq("process_id", "IT-PROC-A")), USER_A, true, "fr"));

        assertEquals(List.of(PROCESS_DESCRIPTION), ids(result));
    }

    @Test
    void valuesAreBoundNotInjected() {
        var result = lookup.find(request(List.of(op("title", FilterOp.CONTAINS, "x' OR '1'='1")), USER_A, true, "fr"));

        assertTrue(result.documents().isEmpty());
        assertEquals(4, ids(lookup.find(request(List.of(eq("doc_id", "IT-D1")), USER_A, false, "fr"))).size());
    }

    @Test
    void unknownKeysAreRejected() {
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class,
                () -> lookup.find(request(List.of(eq("user_uuid", "")), USER_A, true, "fr")));
    }
}
