package zas.admin.zec.backend.observability;

import zas.admin.zec.backend.rag.TurnUsage;

/**
 * Tokens consommés par un tour terminé (ou interrompu après la trace), publié à l'enregistrement de la réponse.
 *
 * @param usage     usage du tour
 * @param workspace workspace du message, {@code null} si aucun
 */
public record TurnUsageEvent(TurnUsage usage, String workspace) {
}
