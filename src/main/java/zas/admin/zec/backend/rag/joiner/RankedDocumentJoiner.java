package zas.admin.zec.backend.rag.joiner;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import zas.admin.zec.backend.rag.validation.SourceValidator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RankedDocumentJoiner implements DocumentJoiner {

    private final SourceValidator sourceValidator;
    private final int topK;

    public RankedDocumentJoiner(ChatModel chatModel, int topK) {
        this.sourceValidator = new SourceValidator(chatModel);
        this.topK = topK;
    }

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
        return documentsForQuery.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .flatMap(List::stream))
                        //.filter(doc -> sourceValidator.isValidSource(entry.getKey().text(), "fr", doc)))
                .collect(Collectors.toMap(Document::getId, Function.identity(), (existing, duplicate) -> existing))
                .values().stream()
                .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                .limit(topK)
                .toList();
    }
}
