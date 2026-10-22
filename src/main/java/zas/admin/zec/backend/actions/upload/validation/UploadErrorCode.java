package zas.admin.zec.backend.actions.upload.validation;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Codes d'erreur stables renvoyés au client lors d'un upload (document personnel, pièce jointe, upload admin).
 * Le client les traduit dans la langue de l'utilisateur.
 * <p>
 * Les codes {@code FILE_TOO_LARGE}, {@code FILE_TYPE_UNSUPPORTED} et {@code FILE_CONTENT_INVALID} sont aussi
 * produits par {@link MultipartFileValidator}. Les codes {@code EMBEDDING_*} et {@code OCR_*} ne sont jamais
 * renvoyés de manière synchrone : ils sont persistés avec le statut {@code FAILED} du traitement asynchrone.
 */
@Getter
public enum UploadErrorCode {
    NO_FILE(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST),
    FILE_CONTENT_INVALID(HttpStatus.BAD_REQUEST),
    CSV_CONTENT_INVALID(HttpStatus.BAD_REQUEST),
    FILE_READ_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),

    /** Le service d'embedding a refusé le document (erreur client définitive). */
    EMBEDDING_REJECTED(HttpStatus.INTERNAL_SERVER_ERROR),
    /** Le traitement d'embedding a dépassé le délai maximal. */
    EMBEDDING_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR),
    /** Aucun texte exploitable n'a pu être extrait du document. */
    EMBEDDING_NO_CONTENT(HttpStatus.INTERNAL_SERVER_ERROR),
    /** Échec du job d'embedding côté service ou nombre maximal de tentatives atteint. */
    EMBEDDING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    /** Échec de l'OCR (erreur du modèle de vision, etc.). */
    OCR_FAILED(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    UploadErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
}
