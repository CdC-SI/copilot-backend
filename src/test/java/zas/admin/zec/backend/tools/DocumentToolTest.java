package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import zas.admin.zec.backend.tools.document.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Plafond de documents par appel de {@code get_documents}, valeurs par défaut, dépôt des documents et erreurs. */
class DocumentToolTest {

    private final List<DocumentLookup.LookupRequest> requests = new ArrayList<>();

    /** Collection fictive contenant {@code available} documents. */
    private DocumentLookup lookup(int available) {
        return new DocumentLookup() {
            public DocumentCollection collection() {
                return DocumentCollection.DOCUMENTATION;
            }

            public List<String> filterableKeys() {
                return List.of("doc_id");
            }

            public LookupResult find(LookupRequest request) {
                requests.add(request);
                var documents = IntStream.range(0, Math.min(available, request.limit()))
                        .mapToObj(i -> Document.builder().id("d" + i).text("t" + i)
                                .metadata(Map.of(MetadataFormatter.META_COLLECTION, "documentation")).build())
                        .toList();
                return new LookupResult(documents, available > request.limit(), null);
            }
        };
    }

    private String call(DocumentTool tool, Integer limit) {
        return tool.getDocuments(DocumentCollection.DOCUMENTATION,
                List.of(new MetadataFilterSpec("doc_id", FilterOp.EQ, "D1", null)), ReturnType.METADATA, limit,
                new ToolContext(Map.of()));
    }

    @Test
    void limitIsCappedAtFiveDocumentsPerCall() {
        var tool = new DocumentTool(List.of(lookup(8)));

        String result = call(tool, 20);
        call(tool, null);
        call(tool, 2);

        assertEquals(List.of(5, 5, 2), requests.stream().map(DocumentLookup.LookupRequest::limit).toList());
        assertTrue(result.contains("at most 5 per call"), result);
    }

    @Test
    void toolDescriptionAnnouncesTheCap() throws NoSuchMethodException {
        var method = DocumentTool.class.getMethod("getDocuments", DocumentCollection.class, List.class,
                ReturnType.class, Integer.class, ToolContext.class);
        String description = method.getAnnotation(Tool.class).description();

        assertTrue(description.contains("au moins un filtre est requis. Au plus 5 documents par appel : "), description);
        assertTrue(description.contains("nouvel appel. Pour BUSINESS_PROCESS"), description);
    }

    /** Collection configurable : résultat fixe, ou exception levée par la recherche. */
    private DocumentLookup lookup(DocumentCollection collection, DocumentLookup.LookupResult result,
                                  RuntimeException failure) {
        return new DocumentLookup() {
            public DocumentCollection collection() {
                return collection;
            }

            public List<String> filterableKeys() {
                return List.of("doc_id");
            }

            public LookupResult find(LookupRequest request) {
                requests.add(request);
                if (failure != null) {
                    throw failure;
                }
                return result;
            }
        };
    }

    private static Document chunk(String id) {
        return Document.builder().id(id).text("Texte " + id)
                .metadata(Map.of(MetadataFormatter.META_COLLECTION, "documentation", "doc_id", "D1")).build();
    }

    private static final List<MetadataFilterSpec> FILTERS = List.of(new MetadataFilterSpec("doc_id", FilterOp.EQ, "D1", null));

    @Test
    void defaultsToDocumentationWithContentAndMetadataAndDepositsTheDocumentsWithCitationRefs() {
        var tool = new DocumentTool(List.of(lookup(DocumentCollection.DOCUMENTATION,
                new DocumentLookup.LookupResult(List.of(chunk("d1"), chunk("d2")), false, null), null)));
        var retrieved = new ArrayList<Document>();
        var refs = new zas.admin.zec.backend.rag.CitationRefs();
        Map<String, Object> context = Map.of(ToolContextKeys.CTX_USER_ID, "u1", ToolContextKeys.CTX_LANGUAGE, "it",
                ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, retrieved, ToolContextKeys.CTX_CITATION_REFS, refs);

        String result = tool.getDocuments(null, FILTERS, null, null, new ToolContext(context));

        var request = requests.getFirst();
        assertEquals("u1", request.userId());
        assertEquals("it", request.language());
        assertTrue(request.includeContent());
        assertTrue(result.contains("Texte d1"), result);
        assertTrue(result.contains("ref=\"2\""), result);
        assertTrue(result.contains("<metadata>"), result);
        assertEquals(List.of("d1", "d2"), retrieved.stream().map(Document::getId).toList());
        assertEquals(Map.of(1, "d1", 2, "d2"), refs.table());
    }

    @Test
    void metadataOnlyResultsGetNoCitationRef() {
        var tool = new DocumentTool(List.of(lookup(DocumentCollection.DOCUMENTATION,
                new DocumentLookup.LookupResult(List.of(chunk("d1")), false, null), null)));
        var refs = new zas.admin.zec.backend.rag.CitationRefs();

        String result = tool.getDocuments(DocumentCollection.DOCUMENTATION, FILTERS, ReturnType.METADATA, 1,
                new ToolContext(Map.of(ToolContextKeys.CTX_CITATION_REFS, refs)));

        assertFalse(result.contains("Texte d1"), result);
        assertFalse(result.contains("ref="), result);
        assertTrue(refs.isEmpty());
        assertFalse(requests.getFirst().includeContent());
    }

    @Test
    void noticeAndEmptyResultAreReportedToTheLlm() {
        var tool = new DocumentTool(List.of(lookup(DocumentCollection.BUSINESS_PROCESS,
                new DocumentLookup.LookupResult(List.of(), false, "Only the latest version is returned."), null)));

        String result = tool.getDocuments(DocumentCollection.BUSINESS_PROCESS, FILTERS, ReturnType.BOTH, 0, null);

        assertTrue(result.startsWith("<notice>Only the latest version is returned.</notice>"), result);
        assertTrue(result.contains(DocumentTool.NO_RESULT), result);
        assertEquals(5, requests.getFirst().limit());
    }

    @Test
    void invalidFiltersAndUnsupportedCollectionsReturnAnError() {
        var tool = new DocumentTool(List.of(lookup(DocumentCollection.DOCUMENTATION, null,
                new JsonbFilterSqlBuilder.InvalidFilterException("Unknown key 'x'."))));

        assertEquals("<error>Unknown key 'x'.</error>",
                tool.getDocuments(DocumentCollection.DOCUMENTATION, FILTERS, ReturnType.BOTH, null, new ToolContext(Map.of())));
        assertEquals("<error>Unsupported collection 'BUSINESS_PROCESS'.</error>",
                tool.getDocuments(DocumentCollection.BUSINESS_PROCESS, FILTERS, ReturnType.BOTH, null, new ToolContext(Map.of())));
    }

    @Test
    void exhaustedBudgetSkipsTheLookup() {
        var tool = new DocumentTool(List.of(lookup(8)));

        String result = tool.getDocuments(DocumentCollection.DOCUMENTATION, FILTERS, ReturnType.BOTH, null,
                new ToolContext(Map.of(ToolContextKeys.CTX_TOOL_CALL_BUDGET, new AtomicInteger(0))));

        assertEquals(ToolCallBudget.EXHAUSTED_MESSAGE, result);
        assertTrue(requests.isEmpty());
    }
}
