package zas.admin.zec.backend.actions.converse;

import reactor.core.publisher.Flux;
import zas.admin.zec.backend.rag.token.Token;

import java.util.List;

/**
 * Stratégie de génération de réponse pour une {@link Question} donnée (pattern Strategy).
 *
 * <p>Deux implémentations coexistent :</p>
 * <ul>
 *   <li>{@link RAGChatService} : fonctionnalités complètes (RAG + pièces jointes).</li>
 *   <li>{@link AttachmentChatService} : uniquement les pièces jointes, sans recherche documentaire.</li>
 * </ul>
 *
 * <p>{@link ChatServiceFactory} sélectionne l'implémentation à utiliser en fonction du
 * {@link ConversationType} de la conversation (déterminé une fois pour toutes à sa création,
 * cf. {@link ConversationType}).</p>
 */
public interface ChatService {

    /**
     * Indique si cette stratégie doit être utilisée pour le type de conversation donné.
     */
    boolean supports(ConversationType conversationType);

    /**
     * Génère la réponse (streamée) à la question, en tenant compte de l'historique de conversation.
     */
    Flux<Token> answer(Question question, String userId, List<Message> conversationHistory);
}
