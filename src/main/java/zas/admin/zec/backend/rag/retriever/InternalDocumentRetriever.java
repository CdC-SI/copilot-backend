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

public final class InternalDocumentRetriever implements DocumentRetriever {

    private final DocumentRetriever publicDocumentRetriever;
    private final VectorStore internalDocumentStore;
    private final Integer topK;
    private final Supplier<Filter.Expression> filterExpression;

    public InternalDocumentRetriever(DocumentRetriever publicDocumentRetriever, VectorStore internalDocumentStore,
                                     @Nullable Integer topK, @Nullable Filter.Expression filterExpression) {

        this.publicDocumentRetriever = publicDocumentRetriever;
        this.internalDocumentStore = internalDocumentStore;
        this.topK = topK != null ? topK : 5;
        this.filterExpression = filterExpression != null ? () -> filterExpression : () -> null;
    }

    @Override
    public List<Document> retrieve(Query query) {
        var publicDocuments = publicDocumentRetriever.retrieve(query);
        var internalRetriever = VectorStoreDocumentRetriever.builder()
                .similarityThreshold(0.0)
                .filterExpression(filterExpression)
                .vectorStore(internalDocumentStore)
                .topK(topK)
                .build();

        publicDocuments.addAll(internalRetriever.retrieve(query));
        return publicDocuments;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DocumentRetriever publicDocumentRetriever;
        private VectorStore internalDocumentStore;
        private Integer topK;
        private Filter.Expression filterExpression;

        public Builder publicDocumentRetriever(DocumentRetriever publicRetriever) {
            this.publicDocumentRetriever = publicRetriever;
            return this;
        }

        public Builder internalDocumentStore(VectorStore internalDocumentStore) {
            this.internalDocumentStore = internalDocumentStore;
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

        public InternalDocumentRetriever build() {
            return new InternalDocumentRetriever(publicDocumentRetriever, internalDocumentStore, topK, filterExpression);
        }
    }
}
