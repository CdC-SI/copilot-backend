package zas.admin.zec.backend.actions.summarize;

import java.time.LocalDateTime;

public record SummaryTaskResponse(
        Long id,
        String navs,
        SummaryTaskStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

