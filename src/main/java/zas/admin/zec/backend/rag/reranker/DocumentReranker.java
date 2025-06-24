package zas.admin.zec.backend.rag.reranker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.config.properties.RerankingProperties;
import zas.admin.zec.backend.rag.PublicDocument;

import java.util.List;

@Slf4j
@Service
public class DocumentReranker {

    private final WebClient rerankerClient;

    public DocumentReranker(WebClient.Builder builder, RerankingProperties rerankingProperties) {
        this.rerankerClient = builder
                .baseUrl("https://api.cohere.com/v2/rerank")
                .defaultHeader("Authorization", "Bearer " + rerankingProperties.cohereApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public List<PublicDocument> rerank(List<PublicDocument> docsToRerank, Question question) {
        log.info("Reranking {} documents based on the question: {}", docsToRerank.size(), question.query());

        RerankRequest requestBody = new RerankRequest(
            "rerank-multilingual-v3.0",
            question.query(),
            docsToRerank.stream().map(PublicDocument::text).toList()
        );

        try {
            RerankResponse response = rerankerClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(RerankResponse.class)
                    .block();

            if (response == null || response.results().isEmpty()) {
                log.warn("Reranking failed or returned no results. Returning original documents.");
                return docsToRerank;
            }

            return response.results().stream()
                    .map(result -> docsToRerank.get(result.index()))
                    .limit(question.kRetrieve())
                    .toList();
        } catch (WebClientRequestException e) {
            log.error("Error during reranking request: {}", e.getMessage());
            return docsToRerank;
        }
    }
}
