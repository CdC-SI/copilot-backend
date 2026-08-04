package zas.admin.zec.backend.actions.workspace;

public class WorkspaceAlreadyExistsException extends RuntimeException {
    public WorkspaceAlreadyExistsException(String message) {
        super(message);
    }
}
