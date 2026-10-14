package zas.admin.zec.backend.actions.converse;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.ToolContextKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base commune aux implémentations de {@link ChatService}, regroupant les helpers partagés :
 * conversion de l'historique en messages Spring AI, extraction du texte d'un {@link ChatResponse},
 * et construction du socle du {@code ToolContext} (données non fournies par le LLM).
 */
abstract class AbstractChatService implements ChatService {

    /**
     * Construit le socle du {@code ToolContext} commun à toutes les stratégies : identité de
     * l'utilisateur, langue, conversation courante et sink de statut partagé. Les implémentations
     * peuvent y ajouter leurs propres clés (documents récupérés, workspace résolu, etc.).
     */
    protected Map<String, Object> baseToolContext(Question question, String userId, Sinks.Many<Token> statusSink) {
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(ToolContextKeys.CTX_USER_ID, userId);
        toolContext.put(ToolContextKeys.CTX_LANGUAGE, question.language());
        toolContext.put(ToolContextKeys.CTX_CONVERSATION_ID, question.conversationId());
        toolContext.put(ToolContextKeys.CTX_STATUS_SINK, statusSink);
        return toolContext;
    }

    protected Flux<Token> toTextToken(ChatResponse response) {
        return Flux.just(new TextToken(extractText(response)));
    }

    protected String extractText(ChatResponse response) {
        return Optional.ofNullable(response)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .orElse("");
    }

    /**
     * Convertit l'historique en messages Spring AI en préfixant chaque message utilisateur par le bloc
     * {@code <retrieved_sources>} des documents consultés lors du tour assistant précédent.
     * Le rendu est déterministe : un même historique produit toujours le même préfixe (prefix caching).
     */
    protected List<org.springframework.ai.chat.messages.Message> toPromptMessages(List<Message> history) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(history.size());
        String pendingBlock = "";
        int assistantTurn = 0;
        for (Message message : history) {
            if ("USER".equals(message.role())) {
                messages.add(new UserMessage(withContextBlock(pendingBlock, message.message())));
                pendingBlock = "";
            } else {
                assistantTurn++;
                messages.add(new AssistantMessage(message.message()));
                pendingBlock = RetrievedSourcesRenderer.render(assistantTurn,
                        RetrievedSourcesRenderer.consultedSources(message));
            }
        }
        return messages;
    }

    /** Bloc {@code <retrieved_sources>} du dernier tour assistant, à préfixer à la question courante. */
    protected String retrievedSourcesForCurrentTurn(List<Message> history) {
        if (history.isEmpty() || "USER".equals(history.getLast().role())) {
            return "";
        }
        long assistantTurns = history.stream().filter(m -> !"USER".equals(m.role())).count();
        return RetrievedSourcesRenderer.render((int) assistantTurns,
                RetrievedSourcesRenderer.consultedSources(history.getLast()));
    }

    /** Préfixe {@code text} par un bloc ajouté par le système ({@code <retrieved_sources>}, {@code <conversation_attachments>}). */
    protected static String withContextBlock(String block, String text) {
        return block == null || block.isEmpty() ? text : block + "\n\n" + text;
    }

    protected org.springframework.ai.chat.messages.Message convertToMessage(Message message) {
        return "USER".equals(message.role())
                ? new UserMessage(message.message())
                : new AssistantMessage(message.message());
    }
}
