package zas.admin.zec.backend.persistence.entity;

/**
 * Cycle de vie d'un feedback (message ou source) du point de vue de sa prise en charge
 * par les modérateurs/administrateurs.
 */
public enum FeedbackStatus {
    NEW,
    TREATED,
    OBSOLETE
}
