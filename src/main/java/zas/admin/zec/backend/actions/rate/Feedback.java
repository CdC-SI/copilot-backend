package zas.admin.zec.backend.actions.rate;

import jakarta.annotation.Nullable;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;

public record Feedback(
        String conversationId,
        String messageId,
        boolean isPositive,
        @Nullable String comment,
        @Nullable String question,
        @Nullable String answer,
        @Nullable FeedbackCategory category) {
}
