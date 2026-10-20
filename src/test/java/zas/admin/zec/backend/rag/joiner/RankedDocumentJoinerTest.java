package zas.admin.zec.backend.rag.joiner;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Fusion des résultats des requêtes étendues : un document trouvé par plusieurs requêtes n'apparaît qu'une fois. */
class RankedDocumentJoinerTest {

    private static Document doc(String id, double score) {
        return Document.builder().id(id).text(id).score(score).build();
    }

    @Test
    void keepsEachDocumentOnceWithItsBestScore() {
        var reranker = mock(DocumentReranker.class);
        when(reranker.isEnabled()).thenReturn(true);
        when(reranker.getTopK()).thenReturn(10);
        when(reranker.getScoreThreshold()).thenReturn(0.1f);
        when(reranker.rerank(anyString(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        Map<Query, List<List<Document>>> results = new LinkedHashMap<>();
        results.put(new Query("q1"), List.of(List.of(doc("a", 0.4), doc("b", 0.9))));
        results.put(new Query("q2"), List.of(List.of(doc("a", 0.8), doc("c", 0.05))));

        var joined = new RankedDocumentJoiner(reranker).join(results);

        assertEquals(List.of("b", "a"), joined.stream().map(Document::getId).toList());
        assertEquals(0.8, joined.get(1).getScore(), 1e-9);
    }
}
