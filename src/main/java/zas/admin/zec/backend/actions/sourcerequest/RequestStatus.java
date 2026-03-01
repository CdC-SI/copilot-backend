package zas.admin.zec.backend.actions.sourcerequest;

/**
 * Statut d'une demande de source.
 */
public enum RequestStatus {
    /**
     * Demande créée, en attente de traitement.
     */
    WAITING,

    /**
     * Demande en cours de traitement par un administrateur.
     */
    PROCESSING,

    /**
     * Source intégrée au système.
     */
    INTEGRATED;

    /**
     * Vérifie si la transition vers un nouveau statut est valide.
     * Empêche les transitions incohérentes (ex: INTEGREE → EN_ATTENTE).
     *
     * @param newStatus le nouveau statut souhaité
     * @return true si la transition est valide
     */
    public boolean canTransitionTo(RequestStatus newStatus) {
        if (this == newStatus) {
            return true;
        }

        return switch (this) {
            case WAITING -> newStatus == PROCESSING || newStatus == INTEGRATED;
            case PROCESSING -> newStatus == INTEGRATED || newStatus == WAITING;
            case INTEGRATED -> false; // Une source intégrée ne peut pas changer de statut
        };
    }
}
