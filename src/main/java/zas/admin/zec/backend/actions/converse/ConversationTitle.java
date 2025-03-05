package zas.admin.zec.backend.actions.converse;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDateTime;

public record ConversationTitle(
        @JsonAlias("chat_title") String title,
        @JsonAlias("user_uuid") String userId,
        @JsonAlias("conversation_uuid") String conversationId,
        LocalDateTime timestamp) {}