package zas.admin.zec.backend.actions.upload.validation;

public class UploadException extends RuntimeException {
    private final String fileName;
    public UploadException(String fileName, String s, Exception e) {
        super(s, e);
        this.fileName = fileName;
    }
    public String getFileName() {
        return fileName;
    }
}
