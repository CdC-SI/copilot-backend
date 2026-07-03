package zas.admin.zec.backend.actions.source;

public class SourceAlreadyExistsException extends RuntimeException {
    public SourceAlreadyExistsException(String message) {
        super(message);
    }
}
