package zas.admin.zec.backend.rag.retriever;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.rag.PublicDocument;
import zas.admin.zec.backend.tools.EntityMapper;

import java.util.Arrays;
import java.util.List;

@Component
public final class TopKRetriever implements IDocumentRetriever {

    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final EntityMapper entityMapper;

    public TopKRetriever(DocumentRepository documentRepository, @Qualifier("publicEmbeddingModel") EmbeddingModel embeddingModel, EntityMapper entityMapper) {
        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
        this.entityMapper = entityMapper;
    }

    @Override
    public RetrievalMethod getRetrievalMethod() {
        return RetrievalMethod.TOP_K;
    }

    @Override
    public List<PublicDocument> retrieveRelatedDocuments(String query, int kRetrieve) {
        String questionEmbed = Arrays.toString(embeddingModel.embed(query));
        return documentRepository.findNearestsByTextEmbedding(questionEmbed, kRetrieve)
                .stream()
                .map(entityMapper::map)
                .toList();
    }
}
