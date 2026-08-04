package zas.admin.zec.backend.actions.converse;

import jakarta.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record Message(
        @Nullable String messageId,
        @Nullable String userId,
        @Nullable String conversationId,
        @Nullable UUID faqItemId,
        String language,
        String message,
        String role,
        @Nullable List<Source> sources,
        @Nullable List<String> suggestions,
        LocalDateTime timestamp,
        @Nullable String workspace) {
}
