package zas.admin.zec.backend.rag.retriever;

import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.Document;

import java.util.List;

public sealed interface IDocumentRetriever permits TopKRetriever {
    RetrievalMethod getRetrievalMethod();
    List<Document> retrieveRelatedDocuments(Question question);
}
