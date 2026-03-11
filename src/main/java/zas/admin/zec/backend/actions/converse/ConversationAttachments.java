package zas.admin.zec.backend.actions.converse;

import java.util.List;

public record ConversationAttachments(String conversationId, List<Attachment> attachments) {
}
