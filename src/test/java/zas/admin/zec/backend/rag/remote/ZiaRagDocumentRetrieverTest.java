package zas.admin.zec.backend.rag.remote;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Requête envoyée à zia-rag : question, filtre de sources et utilisateur, sans profil de recherche. */
class ZiaRagDocumentRetrieverTest {

    private final ZiaRagClient client = mock(ZiaRagClient.class);
    private final ZiaRagDocumentRetriever retriever = new ZiaRagDocumentRetriever(client);

    @Test
    void sendsTheQuestionWithTheSourceFilterAndReturnsZiaRagDocumentsAsIs() {
        var fromZiaRag = List.of(doc("b", 0.9), doc("a", 0.6));
        when(client.retrieve(any(), any())).thenReturn(fromZiaRag);

        var documents = retriever.retrieve("q", List.of("afac"), "u1", "Bearer jwt");

        assertSame(fromZiaRag, documents);
        verify(client).retrieve(eq(new ZiaRagClient.Request("q",
                new ZiaRagClient.Config(Map.of("source", List.of("afac"))), "u1")), eq("Bearer jwt"));
    }

    @Test
    void omitsTheConfigWithoutSourcesAndTheUserWhenUnknown() {
        when(client.retrieve(any(), any())).thenReturn(List.of());

        retriever.retrieve("q", List.of(), " ", null);

        verify(client).retrieve(eq(new ZiaRagClient.Request("q", null, null)), isNull());
    }

    @Test
    void propagatesZiaRagFailures() {
        when(client.retrieve(argThat(request -> request != null), any()))
                .thenThrow(new IllegalStateException("zia-rag indisponible"));

        assertThrows(IllegalStateException.class, () -> retriever.retrieve("q", List.of(), "u1", null));
    }

    private static Document doc(String id, double score) {
        return Document.builder().id(id).text(id).score(score).build();
    }
}
