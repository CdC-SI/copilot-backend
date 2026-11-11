package zas.admin.zec.backend.actions.converse;

import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;

/**
 * @param errorCode cause de l'échec de l'OCR, renseignée uniquement si {@code status == FAILED}
 */
public record Attachment(Long id, String filename, Long fileSize, AttachmentStatus status, UploadErrorCode errorCode) {
}
