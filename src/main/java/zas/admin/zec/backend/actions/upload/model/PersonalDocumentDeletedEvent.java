package zas.admin.zec.backend.actions.upload.model;

/**
 * Publié lors de la suppression d'un document personnel dont le job d'embedding est encore en cours.
 */
public record PersonalDocumentDeletedEvent(String jobId, String userUuid) {}
