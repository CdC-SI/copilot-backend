package zas.admin.zec.backend.rag.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;

import java.util.ArrayList;
import java.util.List;

@Component
public class LegacyDocumentRetriever implements DocumentRetriever {

    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;

    public LegacyDocumentRetriever(DocumentRepository documentRepository,
                                   @Qualifier("publicEmbeddingModel") EmbeddingModel embeddingModel) {

        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Document> retrieve(Query query) {
        return new ArrayList<>();

    }
}
