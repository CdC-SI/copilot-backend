package zas.admin.zec.backend.actions.summarize;

public enum SummaryTaskStatus {
    EN_COURS("En cours de traitement"),
    TERMINEE("Traitement terminé"),
    ERREUR("Erreur lors du traitement");

    private final String description;

    SummaryTaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

