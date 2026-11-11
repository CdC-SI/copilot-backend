package zas.admin.zec.backend.actions.upload.model;

import java.util.Arrays;

/**
 * Statut d'un job côté service externe d'embedding ({@code GET /jobs/{id}}).
 */
public enum EmbeddingJobStatus {
    QUEUED("queued"),
    RUNNING("running"),
    COMPLETED("completed"),
    COMPLETED_WITH_ERRORS("completed_with_errors"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown");

    private final String value;

    EmbeddingJobStatus(String value) {
        this.value = value;
    }

    public static EmbeddingJobStatus from(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equalsIgnoreCase(value))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public boolean hasResult() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS;
    }

    public boolean isUnsuccessfulTerminal() {
        return this == FAILED || this == CANCELLED;
    }
}
