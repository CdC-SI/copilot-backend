package zas.admin.zec.backend.actions.converse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;
import zas.admin.zec.backend.rag.TurnTrace;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param turnTrace   trace du tour de l'agent (statut + documents consultés), sur les messages assistant
 *                    uniquement ; usage interne (contexte LLM), non exposée au frontend.
 * @param interrupted réponse assistant interrompue par l'utilisateur (Stop) : seul le texte streamé avant l'arrêt
 *                    a été enregistré ; {@code false} pour les messages utilisateur.
 */
public record Message(
        @Nullable String messageId,
        @Nullable String userId,
        @Nullable String conversationId,
        @Nullable UUID faqItemId,
        String language,
        String message,
        String role,
        @Nullable List<Source> sources,
        @Nullable List<String> suggestions,
        LocalDateTime timestamp,
        @Nullable String workspace,
        @JsonIgnore @Nullable TurnTrace turnTrace,
        boolean interrupted) {

    public Message(@Nullable String messageId, @Nullable String userId, @Nullable String conversationId,
                   @Nullable UUID faqItemId, String language, String message, String role,
                   @Nullable List<Source> sources, @Nullable List<String> suggestions, LocalDateTime timestamp,
                   @Nullable String workspace, @Nullable TurnTrace turnTrace) {
        this(messageId, userId, conversationId, faqItemId, language, message, role, sources, suggestions,
                timestamp, workspace, turnTrace, false);
    }

    public Message(@Nullable String messageId, @Nullable String userId, @Nullable String conversationId,
                   @Nullable UUID faqItemId, String language, String message, String role,
                   @Nullable List<Source> sources, @Nullable List<String> suggestions, LocalDateTime timestamp,
                   @Nullable String workspace) {
        this(messageId, userId, conversationId, faqItemId, language, message, role, sources, suggestions,
                timestamp, workspace, null, false);
    }
}
