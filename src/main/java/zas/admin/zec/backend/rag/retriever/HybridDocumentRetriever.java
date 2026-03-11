package zas.admin.zec.backend.rag.retriever;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.lang.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retriever hybride qui combine la recherche par similarité vectorielle (toujours active)
 * et une recherche BM25 full-text optionnelle. Les résultats sont fusionnés en supprimant
 * les doublons (par ID de document).
 * <p>
 * En cas de doublon, le document du vector store est conservé (priorité à la recherche sémantique),
 * car le reranker en aval se chargera du classement final.
 */
@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {

    private final DocumentRetriever vectorRetriever;
    private final BM25DocumentRetriever bm25Retriever;

    public HybridDocumentRetriever(DocumentRetriever vectorRetriever, BM25DocumentRetriever bm25Retriever) {
        this.vectorRetriever = vectorRetriever;
        this.bm25Retriever = bm25Retriever;
    }

    @Override
    @NonNull
    public List<Document> retrieve(@NonNull Query query) {
        // 1. Recherche vectorielle (toujours active)
        List<Document> vectorResults = vectorRetriever.retrieve(query);
        log.debug("Recherche vectorielle: {} documents trouvés", vectorResults.size());

        // 2. Recherche BM25 full-text
        List<Document> bm25Results = bm25Retriever.retrieve(query);
        log.debug("Recherche BM25: {} documents trouvés", bm25Results.size());

        // 3. Fusion avec déduplication par ID (priorité aux résultats vectoriels)
        Map<String, Document> merged = new LinkedHashMap<>();

        for (Document doc : vectorResults) {
            merged.put(doc.getId(), doc);
        }

        for (Document doc : bm25Results) {
            merged.putIfAbsent(doc.getId(), doc);
        }

        log.debug("Résultats hybrides après fusion: {} documents (vector={}, bm25={}, dédupliqués={})",
                merged.size(), vectorResults.size(), bm25Results.size(),
                vectorResults.size() + bm25Results.size() - merged.size());

        return List.copyOf(merged.values());
    }
}



