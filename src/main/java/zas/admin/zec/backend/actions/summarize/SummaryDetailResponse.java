package zas.admin.zec.backend.actions.summarize;

import java.time.LocalDateTime;

public record SummaryDetailResponse(
        Long id,
        String navs,
        String summaryMarkdown,
        LocalDateTime updatedAt
) {
}

