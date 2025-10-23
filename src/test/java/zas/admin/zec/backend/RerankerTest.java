package zas.admin.zec.backend;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RerankerTest {

    @Autowired
    private DocumentReranker documentReranker;

    @Test
    void reranker_should_set_new_scores_on_documents() {
        String query = "What is the capital of France?";
        List<String> texts = List.of(
                "Paris is the capital of France.",
                "Berlin is the capital of Germany.",
                "Madrid is the capital of Spain."
        );
        List<Document> documents = texts.stream()
                .map(text -> Document.builder().text(text).score(0.1).build())
                .toList();

        List<Document> reranked = documentReranker.rerank(query, documents);
        assertThat(reranked)
                .extracting(Document::getText)
                .containsExactly(
                        "Paris is the capital of France.",
                        "Berlin is the capital of Germany.",
                        "Madrid is the capital of Spain.");

        assertThat(reranked)
                .extracting(Document::getScore)
                .satisfiesExactly(
                        score -> assertThat(score).isGreaterThan(0.5),
                        score -> assertThat(score).isLessThanOrEqualTo(0.5),
                        score -> assertThat(score).isLessThanOrEqualTo(0.5)
                );
    }
}
