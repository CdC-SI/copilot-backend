package zas.admin.zec.backend.actions.converse;

public record AttachmentUploadResponse(boolean success, String message, ConversationAttachments conversationAttachments) {
        public static AttachmentUploadResponse success(ConversationAttachments conversationAttachments) {
            return new AttachmentUploadResponse(true, "Attachment uploaded successfully", conversationAttachments);
        }

        public static AttachmentUploadResponse failure(String message) {
            return new AttachmentUploadResponse(false, message, null);
        }
}
