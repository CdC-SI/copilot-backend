package zas.admin.zec.backend.actions.rate;

import jakarta.annotation.Nullable;

public record SourceFeedback(
        String conversationId,
        String messageId,
        String documentId,
        boolean isPositive,
        @Nullable String comment,
        @Nullable String question,
        @Nullable String answer
) {}
