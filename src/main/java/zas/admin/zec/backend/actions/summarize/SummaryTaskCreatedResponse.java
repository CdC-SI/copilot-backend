package zas.admin.zec.backend.actions.summarize;

public record SummaryTaskCreatedResponse(
        Long id,
        String navs,
        SummaryTaskStatus status,
        String message
) {
}

