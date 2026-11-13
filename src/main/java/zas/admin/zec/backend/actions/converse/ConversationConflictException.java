package zas.admin.zec.backend.actions.converse;

import lombok.Getter;

/**
 * Requête de génération incompatible avec l'état de la conversation (HTTP 409), renvoyée avant l'ouverture du flux.
 */
@Getter
public class ConversationConflictException extends RuntimeException {

    /** Une réponse est déjà en cours de production (ou d'enregistrement) sur la conversation. */
    public static final String GENERATION_IN_PROGRESS = "GENERATION_IN_PROGRESS";
    /** Le message à remplacer n'est pas la dernière réponse de la conversation. */
    public static final String TURN_NOT_REPLACEABLE = "TURN_NOT_REPLACEABLE";

    private final String code;

    public ConversationConflictException(String code) {
        super(code);
        this.code = code;
    }
}
