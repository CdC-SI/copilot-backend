package zas.admin.zec.backend.rag.reranker;

import java.util.List;

public record RerankResponse(
        String id,
        String object,
        Long created,
        String model,
        List<RerankResult> data) {}
