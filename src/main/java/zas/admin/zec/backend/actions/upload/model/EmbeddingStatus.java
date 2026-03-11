package zas.admin.zec.backend.actions.upload.model;

public enum EmbeddingStatus {
    PENDING("En cours de traitement"),
    PROCESSED("Traitement terminé"),
    FAILED("Erreur lors du traitement");

    private final String description;

    EmbeddingStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

