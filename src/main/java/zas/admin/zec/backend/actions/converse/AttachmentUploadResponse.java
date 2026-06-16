package zas.admin.zec.backend.actions.converse;

/**
 * Réponse de l'endpoint POST /attachments (202 Accepted) et de l'endpoint de polling
 * GET /{conversationId}/attachments.
 *
 * <p>Le champ {@code status} reflète le statut agrégé de toutes les pièces jointes :
 * {@link AttachmentStatus#FAILED} si au moins une a échoué, {@link AttachmentStatus#PENDING}
 * si au moins une est encore en cours d'OCR, sinon {@link AttachmentStatus#PROCESSED}.</p>
 */
public record AttachmentUploadResponse(AttachmentStatus status, String message, ConversationAttachments conversationAttachments) {

    /** Réponse immédiate du POST : fichiers persistés, OCR en cours. */
    public static AttachmentUploadResponse pending(ConversationAttachments conversationAttachments) {
        return new AttachmentUploadResponse(AttachmentStatus.PENDING, "Attachments uploaded, OCR in progress", conversationAttachments);
    }

    /**
     * Réponse du polling : statut agrégé calculé à partir des statuts individuels.
     * Règle : FAILED > PENDING > PROCESSED.
     */
    public static AttachmentUploadResponse fromAttachments(ConversationAttachments conversationAttachments) {
        var attachments = conversationAttachments.attachments();
        AttachmentStatus aggregated;
        if (attachments.stream().anyMatch(a -> a.status() == AttachmentStatus.FAILED)) {
            aggregated = AttachmentStatus.FAILED;
        } else if (attachments.stream().anyMatch(a -> a.status() == AttachmentStatus.PENDING)) {
            aggregated = AttachmentStatus.PENDING;
        } else {
            aggregated = AttachmentStatus.PROCESSED;
        }
        return new AttachmentUploadResponse(aggregated, aggregated.getDescription(), conversationAttachments);
    }

    /** Erreur synchrone (ex. lecture des bytes impossible). */
    public static AttachmentUploadResponse failed(String message) {
        return new AttachmentUploadResponse(AttachmentStatus.FAILED, message, null);
    }
}
