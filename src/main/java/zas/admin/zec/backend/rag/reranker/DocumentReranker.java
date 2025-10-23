package zas.admin.zec.backend.rag.reranker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import zas.admin.zec.backend.config.properties.InternalChatModelProperties;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.singletonList;

@Slf4j
@Service
public class DocumentReranker {

    private static final String PREFIX = """
                <|im_start|>system
                Judge whether the Document meets the requirements based on the Query and the Instruct provided. Note that the answer can only be "yes" or "no".<|im_end|>
                <|im_start|>user
                """;

    private static final String SUFFIX = """
                <|im_end|>
                <|im_start|>assistant
                <think>
                
                </think>
                
                """;

    private static final String QUERY_TEMPLATE = """
                {prefix}<Instruct>: {instruction}
                <Query>: {query}
                """;

    private static final String DOCUMENT_TEMPLATE = """
                <Document>: {document}{suffix}
                """;

    private static final String INSTRUCTION = "Given a user search query, determine whether the document can answer the query or not.";

    private final InternalChatModelProperties internalChatModelProperties;
    private final WebClient reranker;

    public DocumentReranker(@Qualifier("clientBuilderForInternalCalls") WebClient.Builder clientBuilder, InternalChatModelProperties internalChatModelProperties) {
        this.internalChatModelProperties = internalChatModelProperties;
        this.reranker = clientBuilder
                .baseUrl(internalChatModelProperties.rerankerBaseUrl())
                .build();
    }

    /**
     * Rerank documents based on their relevance to the query.
     * Update the score of each document in place.
     *
     * @param query the user query
     * @param documents the list of documents to rerank
     * @return the reranked list of documents
     */
    public List<Document> rerank(String query, List<Document> documents) {
        debugBeforeReranking(documents);
        try {
            var response = reranker.post()
                    .uri("/v1/score")
                    .bodyValue(Map.of(
                            "model", internalChatModelProperties.rerankerModel(),
                            "text_1", singletonList(formatQuery(query)),
                            "text_2", formatDocuments(documents),
                            "truncate_prompt_tokens", -1
                    ))
                    .retrieve()
                    .bodyToMono(RerankResponse.class)
                    .block();

            var rerankedDocs = response == null
                    ? documents
                    : updateDocumentScores(documents, response.data());

            debugAfterReranking(rerankedDocs);
            return rerankedDocs;
        } catch (RuntimeException ex) {
            log.error("Error during document reranking, returning original documents", ex);
            return documents;
        }
    }

    private void debugBeforeReranking(List<Document> documents) {
        log.debug("Documents score before reranking: {}", documents.stream()
                .map(doc -> String.format("id=%s, score=%.4f", doc.getId(), doc.getScore()))
                .toList());
    }

    private void debugAfterReranking(List<Document> documents) {
        log.debug("Documents score after reranking: {}", documents.stream()
                .map(doc -> String.format("id=%s, score=%.4f", doc.getId(), doc.getScore()))
                .toList());
    }

    private String formatQuery(String query) {
        return QUERY_TEMPLATE
                .replace("{prefix}", PREFIX)
                .replace("{instruction}", INSTRUCTION)
                .replace("{query}", query);
    }

    private List<String> formatDocuments(List<Document> documents) {
        return documents.stream()
                .map(doc -> DOCUMENT_TEMPLATE
                        .replace("{document}", Objects.requireNonNull(doc.getText()))
                        .replace("{suffix}", SUFFIX))
                .toList();
    }

    private List<Document> updateDocumentScores(List<Document> documents, List<RerankResult> rerankResults) {
        if (rerankResults == null || rerankResults.isEmpty()) {
            log.warn("No rerank results received, returning original documents");
            return documents;
        }

        return rerankResults
                .stream()
                .map(result -> documents.get(result.index())
                        .mutate()
                        .score(result.score())
                        .build())
                .toList();
    }
}
