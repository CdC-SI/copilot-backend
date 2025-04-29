package zas.admin.zec.backend.rag.reranker;

import java.util.List;

public record RerankRequest(String model, String query, List<String> documents) {}
