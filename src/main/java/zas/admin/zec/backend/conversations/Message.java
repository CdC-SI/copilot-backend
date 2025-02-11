package zas.admin.zec.backend.conversations;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.List;

public record Message(
        @Nullable @JsonAlias("message_uuid") String messageId,
        @Nullable @JsonAlias("user_uuid") String userId,
        @Nullable @JsonAlias("conversation_uuid") String conversationId,
        @Nullable @JsonAlias("faq_id") Long faqItemId,
        @JsonAlias("lang") String language,
        String message,
        @JsonAlias("source") String role,
        @Nullable List<String> sources,
        LocalDateTime timestamp) {
}
