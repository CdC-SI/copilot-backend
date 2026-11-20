package zas.admin.zec.backend.actions.converse;

import reactor.core.publisher.Flux;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.ToolCallRecorder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stratégie de génération de réponse pour une {@link Question} donnée (pattern Strategy).
 *
 * <p>Une seule implémentation : {@link RAGChatService} (agent unique : recherche documentaire, documents,
 * pièces jointes et demandes générales). {@link ChatServiceFactory} sélectionne l'implémentation en fonction du
 * {@link ConversationType} de la conversation, ce qui permet d'ajouter d'autres stratégies.</p>
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

    default Flux<Token> answer(Question question, String userId, List<Message> conversationHistory,
                               AtomicReference<ToolCallRecorder> recorderReference) {
        return answer(question, userId, conversationHistory);
    }
}
