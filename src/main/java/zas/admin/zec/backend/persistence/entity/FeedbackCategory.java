package zas.admin.zec.backend.persistence.entity;

/**
 * Qualification optionnelle d'un feedback, généralement renseignée par l'utilisateur
 * lorsqu'il signale un problème (feedback négatif).
 */
public enum FeedbackCategory {
    WRONG_SOURCE,
    WRONG_ANSWER,
    INCOMPLETE_ANSWER
}
