package zas.admin.zec.backend.actions.converse;

import jakarta.annotation.Nullable;

public record Feedback(
        String conversationId,
        String messageId,
        boolean isPositive,
        @Nullable String comment) {
}
