package zas.admin.zec.backend.actions.converse;

import lombok.Getter;

@Getter
public enum AttachmentStatus {
    PENDING("En cours de traitement OCR"),
    PROCESSED("Traitement terminé"),
    FAILED("Erreur lors du traitement OCR");

    private final String description;

    AttachmentStatus(String description) {
        this.description = description;
    }
}


