package zas.admin.zec.backend.actions.converse;

public record Question(
    String query,
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
) {}
