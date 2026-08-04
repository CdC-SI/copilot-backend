package zas.admin.zec.backend.actions.converse;

/**
 * Type d'une conversation, déterminé une fois pour toutes à la première question qui
 * l'initie, puis immuable pendant toute la durée de vie de la conversation.
 *
 * <p>Sert de critère de sélection à {@link ChatServiceFactory} : contrairement au flag
 * {@link Question#ragEnabled()} (qui peut varier d'une question à l'autre au sein d'une
 * même conversation), le {@code ConversationType} est figé et persisté dès la création de
 * la conversation.</p>
 */
public enum ConversationType {

    /** Fonctionnalités complètes : RAG + pièces jointes ({@link RAGChatService}). */
    COMPLETE,

    /** Pièces jointes uniquement, sans recherche documentaire ({@link AttachmentChatService}). */
    NO_RAG
}
