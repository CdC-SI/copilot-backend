package zas.admin.zec.backend.conversations;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDateTime;

public record Message(
        @JsonAlias("message_uuid") String messageId,
        @JsonAlias("user_uuid") String userId,
        @JsonAlias("conversation_uuid") String conversationId,
        String message,
        String role,
        LocalDateTime timestamp) {
}
