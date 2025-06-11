package zas.admin.zec.backend.actions.upload.validation;

public class UploadException extends RuntimeException {
    public UploadException(String s, Exception e) {
        super(s, e);
    }
}
