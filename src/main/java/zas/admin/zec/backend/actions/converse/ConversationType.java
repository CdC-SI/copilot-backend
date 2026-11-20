package zas.admin.zec.backend.actions.converse;

/**
 * Type d'une conversation, persisté dans {@code chat_title} et renvoyé avec les titres.
 *
 * <p>Sert de critère de sélection à {@link ChatServiceFactory}. Depuis l'agent unique (F17), toutes les
 * conversations sont de type {@link #COMPLETE} : l'ancien type {@code NO_RAG} (conversation « sans Corpus »)
 * a été migré (V25).</p>
 */
public enum ConversationType {

    /** Agent unique : recherche documentaire, documents, pièces jointes et demandes générales ({@link RAGChatService}). */
    COMPLETE
}
