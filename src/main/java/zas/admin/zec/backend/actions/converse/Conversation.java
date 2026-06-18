package zas.admin.zec.backend.actions.converse;

import java.util.List;

public record Conversation(String conversationId, String userId, List<Message> messages, List<Attachment> attachments) {
}
