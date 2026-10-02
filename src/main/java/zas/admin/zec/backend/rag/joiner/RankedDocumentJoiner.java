package zas.admin.zec.backend.rag.joiner;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RankedDocumentJoiner implements DocumentJoiner {

    private static final int DEFAULT_LIMIT_WHEN_RERANKER_DISABLED = 10;

    private final DocumentReranker reranker;
    private final float scoreThreshold;
    private final int topK;
    private final List<Document> conversationDocuments;

    public RankedDocumentJoiner(DocumentReranker reranker, List<Document> conversationDocuments) {
        this.reranker = Objects.requireNonNull(reranker, "reranker must not be null");
        this.scoreThreshold = reranker.getScoreThreshold();
        this.topK = reranker.getTopK();
        this.conversationDocuments = withoutNulls(conversationDocuments);
    }

    @Override
    public @NonNull List<Document> join(@NonNull Map<Query, List<List<Document>>> documentsForQuery) {
        var rankedDocuments = rankRetrievedDocuments(documentsForQuery);
        return combineWithConversationDocuments(rankedDocuments);
    }

    private List<Document> rankRetrievedDocuments(Map<Query, List<List<Document>>> documentsForQuery) {
        if (documentsForQuery == null || documentsForQuery.isEmpty()) {
            return List.of();
        }
        return documentsForQuery.entrySet().stream()
                .flatMap(entry -> rerankForQuery(entry.getKey(), entry.getValue()))
                .filter(this::isAboveThreshold)
                .collect(Collectors.toMap(Document::getId, Function.identity(), RankedDocumentJoiner::highestScore))
                .values().stream()
                .sorted(Comparator.comparingDouble(RankedDocumentJoiner::scoreOf).reversed())
                .limit(resultLimit())
                .toList();
    }

    private Stream<Document> rerankForQuery(Query query, List<List<Document>> documentLists) {
        if (query == null || documentLists == null) {
            return Stream.empty();
        }
        return documentLists.stream()
                .map(RankedDocumentJoiner::withoutNulls)
                .filter(docs -> !docs.isEmpty())
                .flatMap(docs -> rerank(query.text(), docs));
    }

    private Stream<Document> rerank(String queryText, List<Document> documents) {
        var reranked = reranker.rerank(queryText, documents);
        return reranked == null ? Stream.empty() : reranked.stream().filter(Objects::nonNull);
    }

    private boolean isAboveThreshold(Document document) {
        var score = document.getScore();
        return score != null && score > scoreThreshold;
    }

    private long resultLimit() {
        return reranker.isEnabled() ? topK : DEFAULT_LIMIT_WHEN_RERANKER_DISABLED;
    }

    private List<Document> combineWithConversationDocuments(List<Document> rankedDocuments) {
        var result = new ArrayList<Document>(conversationDocuments.size() + rankedDocuments.size());
        result.addAll(conversationDocuments);
        result.addAll(rankedDocuments);
        return result;
    }

    private static Document highestScore(Document existing, Document duplicate) {
        return scoreOf(duplicate) > scoreOf(existing) ? duplicate : existing;
    }

    private static double scoreOf(Document document) {
        var score = document.getScore();
        return score != null ? score : 0.0;
    }

    private static List<Document> withoutNulls(List<Document> documents) {
        if (documents == null) {
            return List.of();
        }
        return documents.stream().filter(Objects::nonNull).toList();
    }
}
