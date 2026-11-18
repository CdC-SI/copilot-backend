package zas.admin.zec.backend.rag.remote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import zas.admin.zec.backend.config.properties.RagRetrievalProperties;
import zas.admin.zec.backend.config.properties.RerankingProperties;
import zas.admin.zec.backend.config.properties.RetrievingProperties;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recherche documentaire de {@code RAGTool} déléguée à zia-rag (T2) : un appel par requête dérivée (réécriture,
 * extension), puis jointure comme {@code RankedDocumentJoiner} (seuil, dédoublonnage au meilleur score, tri, limite).
 * La configuration reprend {@code rag.retrieving} et {@code rag.reranking} : top-k, mode hybride si BM25 est activé,
 * reranker et seuil de score.
 */
@Slf4j
@Component
public class ZiaRagDocumentRetriever {

    static final String META_SOURCE = "source";
    private static final int DEFAULT_LIMIT_WHEN_RERANKER_DISABLED = 10;

    private final ZiaRagClient client;
    private final RagRetrievalProperties retrievalProperties;
    private final RetrievingProperties retrievingProperties;
    private final RerankingProperties rerankingProperties;

    public ZiaRagDocumentRetriever(ZiaRagClient client, RagRetrievalProperties retrievalProperties,
                                   RetrievingProperties retrievingProperties, RerankingProperties rerankingProperties) {
        this.client = client;
        this.retrievalProperties = retrievalProperties;
        this.retrievingProperties = retrievingProperties;
        this.rerankingProperties = rerankingProperties;
    }

    public boolean enabled() {
        return retrievalProperties.ziaRagEnabled();
    }

    /**
     * Documents pertinents pour les requêtes, limités aux sources données (toutes si vide) ; documents publics et
     * privés de {@code userId}. Un échec sur une requête est ignoré tant qu'au moins une requête aboutit.
     */
    public List<Document> retrieve(List<Query> queries, Collection<String> sources, String userId, String bearer) {
        var config = config(sources);
        String user = userId == null || userId.isBlank() ? null : userId;
        List<Document> retrieved = new ArrayList<>();
        RuntimeException lastFailure = null;
        int failures = 0;
        for (Query query : queries) {
            try {
                retrieved.addAll(client.retrieve(new ZiaRagClient.Request(query.text(), config, user), bearer));
            } catch (RuntimeException e) {
                failures++;
                lastFailure = e;
                log.warn("zia-rag : échec de la recherche pour une requête ({})", e.getMessage());
            }
        }
        if (lastFailure != null && failures == queries.size()) {
            throw lastFailure;
        }
        return join(retrieved);
    }

    ZiaRagClient.Config config(Collection<String> sources) {
        boolean reranking = Boolean.TRUE.equals(rerankingProperties.enabled());
        boolean hybrid = retrievingProperties.bm25() != null && Boolean.TRUE.equals(retrievingProperties.bm25().enabled());
        Map<String, List<String>> filters = CollectionUtils.isEmpty(sources)
                ? null : Map.of(META_SOURCE, List.copyOf(sources));
        return new ZiaRagClient.Config(retrievingProperties.topK(), reranking, hybrid ? "HYBRID" : "VECTOR", filters,
                reranking ? rerankingProperties.scoreThreshold().doubleValue() : null);
    }

    List<Document> join(List<Document> documents) {
        float threshold = rerankingProperties.scoreThreshold();
        long limit = Boolean.TRUE.equals(rerankingProperties.enabled())
                ? rerankingProperties.topK() : DEFAULT_LIMIT_WHEN_RERANKER_DISABLED;
        return documents.stream()
                .filter(document -> document.getScore() != null && document.getScore() > threshold)
                .collect(Collectors.toMap(Document::getId, Function.identity(),
                        (existing, duplicate) -> duplicate.getScore() > existing.getScore() ? duplicate : existing,
                        LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                .limit(limit)
                .toList();
    }
}
