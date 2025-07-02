package zas.admin.zec.backend.rag.retriever;

import jakarta.annotation.Nullable;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.function.Supplier;

public final class CopilotDocumentRetriever implements DocumentRetriever {

    private final DocumentRetriever legacyDocumentRetriever;
    private final VectorStore documentStore;
    private final Integer topK;
    private final Supplier<Filter.Expression> filterExpression;

    public CopilotDocumentRetriever(DocumentRetriever legacyDocumentRetriever, VectorStore documentStore,
                                    @Nullable Integer topK, @Nullable Filter.Expression filterExpression) {

        this.legacyDocumentRetriever = legacyDocumentRetriever;
        this.documentStore = documentStore;
        this.topK = topK != null ? topK : 5;
        this.filterExpression = filterExpression != null ? () -> filterExpression : () -> null;
    }

    @Override
    public List<Document> retrieve(Query query) {
        var legacyDocuments = legacyDocumentRetriever.retrieve(query);
        var retriever = VectorStoreDocumentRetriever.builder()
                .filterExpression(filterExpression)
                .vectorStore(documentStore)
                .topK(topK)
                .build();

        legacyDocuments.addAll(retriever.retrieve(query));
        return legacyDocuments;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DocumentRetriever legacyDocumentRetriever;
        private VectorStore documentStore;
        private Integer topK;
        private Filter.Expression filterExpression;

        public Builder legacyDocumentRetriever(DocumentRetriever publicRetriever) {
            this.legacyDocumentRetriever = publicRetriever;
            return this;
        }

        public Builder documentStore(VectorStore vectorStore) {
            this.documentStore = vectorStore;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder filterExpression(Filter.Expression filterExpression) {
            this.filterExpression = filterExpression;
            return this;
        }

        public CopilotDocumentRetriever build() {
            return new CopilotDocumentRetriever(legacyDocumentRetriever, documentStore, topK, filterExpression);
        }
    }
}
