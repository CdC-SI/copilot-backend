package zas.admin.zec.backend.actions.converse;

/**
 * Pièce jointe persistée en attente d'extraction.
 *
 * @param attachmentId ID de l'entité {@code AttachmentEntity}
 * @param pipeline     {@code true} si le texte est extrait par le pdf-ocr-pipeline (PDF), {@code false} pour l'OCR LLM
 */
public record AttachmentUploadedEvent(Long attachmentId, boolean pipeline) {}
