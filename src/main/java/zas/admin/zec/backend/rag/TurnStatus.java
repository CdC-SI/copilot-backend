package zas.admin.zec.backend.rag;

/**
 * Nature de la réponse de l'agent, déclarée par le LLM via la balise {@code <turn_meta status="..."/>}.
 * Pilote l'affichage des sources dans le frontend.
 */
public enum TurnStatus {
    /** Réponse complète : les sources consultées (tour courant + questions de suivi précédentes + réutilisées) sont affichées. */
    ANSWER,
    /** Question de suivi / clarification : aucune source affichée, les sources consultées sont conservées. */
    FOLLOWUP,
    /** Refus ou documentation insuffisante : aucune source affichée. */
    REFUSAL,
    /** Balise absente ou invalide : comportement historique (sources du tour affichées). */
    UNKNOWN;

    public String key() {
        return name().toLowerCase();
    }

    public static TurnStatus fromKey(String key) {
        if (key == null) {
            return UNKNOWN;
        }
        return switch (key.strip().toLowerCase()) {
            case "answer" -> ANSWER;
            case "followup", "follow_up", "follow-up" -> FOLLOWUP;
            case "refusal" -> REFUSAL;
            default -> UNKNOWN;
        };
    }
}
