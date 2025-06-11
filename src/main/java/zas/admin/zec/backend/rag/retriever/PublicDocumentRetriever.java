package zas.admin.zec.backend.rag.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PublicDocumentRetriever implements DocumentRetriever {

    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;

    public PublicDocumentRetriever(DocumentRepository documentRepository,
                                   @Qualifier("publicEmbeddingModel") EmbeddingModel embeddingModel) {

        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Document> retrieve(Query query) {
        String questionEmbed = Arrays.toString(embeddingModel.embed(query.text()));
        return documentRepository.findNearestsProjectionByTextEmbedding(questionEmbed, 5)
                .stream()
                .map(projection -> Document.builder()
                        .text(projection.getText())
                        .metadata("url", projection.getUrl())
                        .score(1 - projection.getDistance())
                        .build())
                .filter(document -> document.getScore() != null && document.getScore() > 0.5)
                .collect(Collectors.toList());
    }
}
