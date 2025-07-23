package zas.admin.zec.backend.actions.converse;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record Question(
    @NotNull String query,
    String language,
    List<String> tags,
    List<String> sources,
    String responseStyle,
    String responseFormat,
    String conversationId) {

    public Question withDefaults() {
        return new Question(
            this.query(),
            this.language() != null ? this.language() : "fr",
            this.tags() != null ? this.tags() : List.of(),
            this.sources() != null ? this.sources() : List.of(),
            this.responseStyle() != null ? this.responseStyle() : "DETAILED",
            this.responseFormat() != null ? this.responseFormat() : "COMPLETE",
            this.conversationId() != null ? this.conversationId() : UUID.randomUUID().toString()
        );
    }
}
