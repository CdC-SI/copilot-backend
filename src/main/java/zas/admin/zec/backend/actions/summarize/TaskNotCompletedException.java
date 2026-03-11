package zas.admin.zec.backend.actions.summarize;

public class TaskNotCompletedException extends RuntimeException {
    public TaskNotCompletedException(String message) {
        super(message);
    }
}

