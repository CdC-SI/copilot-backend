package zas.admin.zec.backend.rag.retriever;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.Document;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentGatherer {

    private final DocumentRetrieverFactory documentRetrieverFactory;
    private final DocumentReranker reranker;

    public DocumentGatherer(DocumentRetrieverFactory documentRetrieverFactory, DocumentReranker reranker) {
        this.documentRetrieverFactory = documentRetrieverFactory;
        this.reranker = reranker;
    }

    public List<Document> retrieveRelatedDocuments(Question question) {
        List<Document> retrievedDocs = Arrays.stream(question.retrievalMethods())
                .parallel()
                .map(RetrievalMethod::valueOf)
                .map(documentRetrieverFactory::getRetriever)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(retriever -> retriever.retrieveRelatedDocuments(question).stream())
                .distinct()
                .toList();

        return reranker.rerank(retrievedDocs, question);
    }
}
