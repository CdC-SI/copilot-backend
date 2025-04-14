package zas.admin.zec.backend.rag.reranker;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RerankResult(int index, @JsonAlias("relevance_score") double relevanceScore) {}
