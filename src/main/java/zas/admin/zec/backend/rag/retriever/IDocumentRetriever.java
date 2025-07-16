package zas.admin.zec.backend.rag.retriever;

import zas.admin.zec.backend.rag.PublicDocument;

import java.util.List;

public sealed interface IDocumentRetriever permits TopKRetriever {
    RetrievalMethod getRetrievalMethod();
    List<PublicDocument> retrieveRelatedDocuments(String query, int kRetrieve);
}
