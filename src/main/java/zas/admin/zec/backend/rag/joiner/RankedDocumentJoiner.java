package zas.admin.zec.backend.rag.joiner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class RankedDocumentJoiner implements DocumentJoiner {

    private final DocumentReranker reranker;
    private final float scoreThreshold;
    private final int topK;
    private final List<Document> conversationDocuments;

    public RankedDocumentJoiner(DocumentReranker reranker, List<Document> conversationDocuments) {
        this.reranker = reranker;
        this.scoreThreshold = reranker.getScoreThreshold();
        this.topK = reranker.getTopK();
        this.conversationDocuments = new ArrayList<>(conversationDocuments);
    }

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
        var preRerankDocs = documentsForQuery.values().stream()
                .flatMap(List::stream)
                .flatMap(List::stream)
                .collect(Collectors.toMap(Document::getId, Function.identity(), (existing, duplicate) -> existing))
                .values().stream()
                .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                .limit(this.topK)
                .toList();
        logDocuments("Before reranking", preRerankDocs);

        var ragRerankedDocs = documentsForQuery.entrySet().stream()
                .flatMap(entry -> entry.getValue()
                        .stream()
                        .flatMap(docs -> reranker.rerank(entry.getKey().text(), docs).stream())
                )
                .filter(doc -> doc.getScore() > this.scoreThreshold)
                .collect(Collectors.toMap(Document::getId, Function.identity(), (existing, duplicate) -> existing))
                .values().stream()
                .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                .limit(reranker.isEnabled() ? this.topK : 10)
                .toList();

        if (!ragRerankedDocs.isEmpty()) {
            logDocuments("Top-1 reranked document", List.of(ragRerankedDocs.get(0)));
        }

        // Combine with conversation documents
        var result = new ArrayList<>(conversationDocuments);
        result.addAll(ragRerankedDocs);
        return result;
    }

    private void logDocuments(String label, List<Document> documents) {
        documents.forEach(doc -> {
            String title = (String) doc.getMetadata().getOrDefault("title", "");
            log.info("{}: id={}, title={}, score={}", label, doc.getId(), title, doc.getScore());
        });
    }
}
