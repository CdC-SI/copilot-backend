package zas.admin.zec.backend.rag.retriever;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.PublicDocument;
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

    public List<PublicDocument> retrieveRelatedDocuments(Question question) {
        List<PublicDocument> retrievedDocs = Arrays.stream(question.retrievalMethods())
                .parallel()
                .map(RetrievalMethod::valueOf)
                .map(documentRetrieverFactory::getRetriever)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(retriever ->
                        retriever.retrieveRelatedDocuments(question.query(), question.kRetrieve()).stream())
                .distinct()
                .toList();

        return reranker.rerank(retrievedDocs, question);
    }
}
