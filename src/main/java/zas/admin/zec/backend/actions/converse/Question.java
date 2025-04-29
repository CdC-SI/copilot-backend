package zas.admin.zec.backend.actions.converse;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record Question(
    @NotNull String query,
    String language,
    String[] tags,
    String[] sources,
    String llmModel,
    Double topP,
    Double temperature,
    Integer maxOutputTokens,
    String[] retrievalMethods,
    Integer kRetrieve,
    Integer kMemory,
    String responseStyle,
    String responseFormat,
    String command,
    String commandArgs,
    Boolean autocomplete,
    Boolean rag,
    Boolean agenticRag,
    Boolean sourceValidation,
    Boolean topicCheck,
    Boolean isFollowUpQ,
    String conversationId
) {
    public Question withDefaults() {
        return new Question(
            this.query(),
            this.language() != null ? this.language() : "fr",
            this.tags() != null ? this.tags() : new String[0],
            this.sources() != null ? this.sources() : new String[0],
            this.llmModel() != null ? this.llmModel() : "gpt-4o-mini",
            this.topP() != null ? this.topP() : 0.9,
            this.temperature() != null ? this.temperature() : 0.7,
            this.maxOutputTokens() != null ? this.maxOutputTokens() : 4096,
            this.retrievalMethods() != null ? this.retrievalMethods() : new String[]{"TOP_K"},
            this.kRetrieve() != null ? this.kRetrieve() : 5,
            this.kMemory() != null ? this.kMemory() : 5,
            this.responseStyle() != null ? this.responseStyle() : "DETAILED",
            this.responseFormat() != null ? this.responseFormat() : "COMPLETE",
            this.command() != null ? this.command() : "",
            this.commandArgs() != null ? this.commandArgs() : "",
            this.autocomplete() == null || this.autocomplete(),
            this.rag() == null || this.rag(),
            this.agenticRag() != null && this.agenticRag(),
            this.sourceValidation() != null && this.sourceValidation(),
            this.topicCheck() != null && this.topicCheck(),
            this.isFollowUpQ() != null && this.isFollowUpQ(),
            this.conversationId() != null ? this.conversationId() : UUID.randomUUID().toString()
        );
    }
}
