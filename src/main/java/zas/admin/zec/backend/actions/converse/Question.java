package zas.admin.zec.backend.actions.converse;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record Question(
    @NotNull String query,
    String language,
    String workspace,
    String responseStyle,
    String responseFormat,
    String conversationId,
    Boolean ragEnabled,
    // Régénération / édition : identifiant de la réponse à remplacer, qui doit être la dernière de la conversation.
    // La dernière question et cette réponse sont exclues du contexte, puis supprimées à l'enregistrement du nouvel échange.
    String replaceMessageId) {

    public Question withDefaults() {
        return new Question(
            this.query(),
            this.language() != null ? this.language() : "fr",
            this.workspace() != null ? this.workspace() : "",
            this.responseStyle() != null ? this.responseStyle() : "DETAILED",
            this.responseFormat() != null ? this.responseFormat() : "COMPLETE",
            this.conversationId() != null ? this.conversationId() : UUID.randomUUID().toString(),
            // Rétrocompatibilité : si absent, toutes les fonctionnalités (RAG + Attachment) sont actives.
            this.ragEnabled() != null ? this.ragEnabled() : Boolean.TRUE,
            this.replaceMessageId() != null && !this.replaceMessageId().isBlank() ? this.replaceMessageId() : null
        );
    }
}
