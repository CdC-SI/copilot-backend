package zas.admin.zec.backend.actions.upload.model;

public enum AvailabilityStatus {
    ACTIVE("Actif"),
    ARCHIVED("Archivé");

    private final String description;

    AvailabilityStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
