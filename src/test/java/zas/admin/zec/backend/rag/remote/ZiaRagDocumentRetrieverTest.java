package zas.admin.zec.backend.rag.remote;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import zas.admin.zec.backend.config.properties.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Configuration envoyée à zia-rag et jointure des résultats des requêtes dérivées. */
class ZiaRagDocumentRetrieverTest {

    private final ZiaRagClient client = mock(ZiaRagClient.class);

    @Test
    void joinsQueriesWithThresholdDeduplicationAndLimit() {
        var retriever = retriever(true, false);
        when(client.retrieve(argThat(request -> request != null && request.question().equals("q1")), any()))
                .thenReturn(List.of(doc("a", 0.6), doc("b", 0.9), doc("low", 0.4)));
        when(client.retrieve(argThat(request -> request != null && request.question().equals("q2")), any()))
                .thenReturn(List.of(doc("a", 0.95), doc("c", 0.7)));

        var documents = retriever.retrieve(List.of(new Query("q1"), new Query("q2")), List.of("afac"), "u1", "Bearer t");

        // Seuil 0.5 strict, meilleur score par id, tri décroissant, top-k 2.
        assertEquals(List.of("a", "b"), documents.stream().map(Document::getId).toList());
        assertEquals(0.95, documents.getFirst().getScore());
        verify(client, times(2)).retrieve(argThat(request -> request.userId().equals("u1")
                && request.config().metadataFilters().equals(Map.of("source", List.of("afac")))), eq("Bearer t"));
    }

    @Test
    void mapsRetrievingAndRerankingSettings() {
        var config = retriever(true, true).config(List.of());
        assertEquals(new ZiaRagClient.Config(5, true, "HYBRID", null, 0.5), config);

        var withoutReranker = retriever(false, false).config(List.of("x"));
        assertEquals(new ZiaRagClient.Config(5, false, "VECTOR", Map.of("source", List.of("x")), null), withoutReranker);
    }

    @Test
    void ignoresAFailedQueryButFailsWhenAllQueriesFail() {
        var retriever = retriever(true, false);
        when(client.retrieve(argThat(request -> request != null && request.question().equals("ok")), any()))
                .thenReturn(List.of(doc("a", 0.8)));
        when(client.retrieve(argThat(request -> request != null && request.question().equals("ko")), any()))
                .thenThrow(new IllegalStateException("zia-rag indisponible"));

        assertEquals(1, retriever.retrieve(List.of(new Query("ok"), new Query("ko")), List.of(), "", null).size());
        assertThrows(IllegalStateException.class,
                () -> retriever.retrieve(List.of(new Query("ko")), List.of(), "", null));
        // Utilisateur inconnu : pas de userId (zia-rag ne cherche alors que dans les documents publics).
        verify(client, atLeastOnce()).retrieve(argThat(request -> request != null && request.userId() == null), any());
    }

    private ZiaRagDocumentRetriever retriever(boolean reranking, boolean bm25) {
        var retrieving = new RetrievingProperties(5, null, new QueryRewriterProperties(false),
                new QueryExpanderProperties(false, 0, false), new Bm25Properties(bm25, 2, "simple"));
        var reranker = new RerankingProperties("key", 0.5f, 1000, 2, reranking);
        var provider = new RagRetrievalProperties(RagRetrievalProperties.Provider.ZIA_RAG, null);
        return new ZiaRagDocumentRetriever(client, provider, retrieving, reranker);
    }

    private static Document doc(String id, double score) {
        return Document.builder().id(id).text(id).score(score).build();
    }
}
