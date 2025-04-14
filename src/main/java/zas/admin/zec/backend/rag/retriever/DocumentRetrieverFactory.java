package zas.admin.zec.backend.rag.retriever;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class DocumentRetrieverFactory {

    private static final Map<RetrievalMethod, IDocumentRetriever> RETRIEVERS = new EnumMap<>(RetrievalMethod.class);

    public DocumentRetrieverFactory(Set<IDocumentRetriever> retrievers) {
        retrievers.forEach(retriever -> RETRIEVERS.put(RetrievalMethod.TOP_K, retriever));
    }

    public Optional<IDocumentRetriever> getRetriever(RetrievalMethod method) {
        return Optional.ofNullable(RETRIEVERS.get(method));
    }
}
