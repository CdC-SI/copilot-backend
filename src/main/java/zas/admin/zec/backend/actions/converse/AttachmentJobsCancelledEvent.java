package zas.admin.zec.backend.actions.converse;

import java.util.List;

/**
 * Pièces jointes supprimées alors que leur job pdf-ocr-pipeline était en cours : les jobs sont annulés
 * après commit de la suppression.
 */
public record AttachmentJobsCancelledEvent(String userId, List<String> jobIds) {}
