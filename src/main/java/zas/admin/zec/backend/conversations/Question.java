package zas.admin.zec.backend.conversations;

import jakarta.annotation.Nullable;

import java.util.List;

public record Question(
        String query,
        boolean autocomplete,
        boolean rag,
        @Nullable String language,
        @Nullable String llmModel,
        @Nullable String responseStyle,
        @Nullable String conversationId,
        @Nullable Integer kMemory,
        @Nullable List<String> tags,
        @Nullable List<String> sources,
        @Nullable List<String> retrievalMethods
        ) {
}
