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
    String conversationId) {

    public Question withDefaults() {
        return new Question(
            this.query(),
            this.language() != null ? this.language() : "fr",
            this.workspace() != null ? this.workspace() : "",
            this.responseStyle() != null ? this.responseStyle() : "DETAILED",
            this.responseFormat() != null ? this.responseFormat() : "COMPLETE",
            this.conversationId() != null ? this.conversationId() : UUID.randomUUID().toString()
        );
    }
}
