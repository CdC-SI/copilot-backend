package zas.admin.zec.backend.actions.workspace;

public class WorkspaceNotFoundException extends RuntimeException {
    public WorkspaceNotFoundException(String message) {
        super(message);
    }
}
