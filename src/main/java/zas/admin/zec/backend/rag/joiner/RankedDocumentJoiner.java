package zas.admin.zec.backend.rag.joiner;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RankedDocumentJoiner implements DocumentJoiner {

    private final DocumentReranker reranker;
    private final float scoreThreshold;
    private final int topK;

    public RankedDocumentJoiner(DocumentReranker reranker) {
        this.reranker = reranker;
        this.scoreThreshold = reranker.getScoreThreshold();
        this.topK = reranker.getTopK();
    }

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
        return documentsForQuery.entrySet().stream()
                .flatMap(entry -> entry.getValue()
                        .stream()
                        .flatMap(docs -> reranker.rerank(entry.getKey().text(), docs).stream())
                )
                .filter(doc -> doc.getScore() > this.scoreThreshold)
                .collect(Collectors.toMap(Document::getId, Function.identity(), (existing, duplicate) -> existing))
                .values().stream()
                .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                .limit(this.topK)
                .toList();
    }
}
