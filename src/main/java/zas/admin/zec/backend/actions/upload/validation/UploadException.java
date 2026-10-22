package zas.admin.zec.backend.actions.upload.validation;

import lombok.Getter;

/**
 * Erreur d'upload portant un {@link UploadErrorCode} renvoyé au client par
 * {@link zas.admin.zec.backend.config.ExceptionsHandler}.
 */
@Getter
public class UploadException extends RuntimeException {

    private final String fileName;
    private final UploadErrorCode errorCode;

    public UploadException(String fileName, UploadErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.fileName = fileName;
        this.errorCode = errorCode;
    }

    public UploadException(String fileName, UploadErrorCode errorCode, String message) {
        this(fileName, errorCode, message, null);
    }
}
