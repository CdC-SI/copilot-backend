package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.tools.document.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Plafond de documents par appel de {@code get_documents}, appliqué et annoncé au LLM. */
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
        var tool = new DocumentTool(List.of(lookup(8)), mock(UserService.class));

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
}
