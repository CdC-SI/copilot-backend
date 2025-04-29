package zas.admin.zec.backend.persistence.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CustomVectorStore implements VectorStore {
    @Override
    public void add(List<Document> documents) {
        log.info("Adding documents: " + documents);
    }

    @Override
    public void delete(List<String> idList) {
        log.info("Deleting documents with IDs: " + idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        log.info("Deleting documents with filter: " + filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        log.info("Performing similarity search with request: " + request);
        return List.of(new Document("Ceci est un test", Map.of("url", "https://example.com")));
    }
}
