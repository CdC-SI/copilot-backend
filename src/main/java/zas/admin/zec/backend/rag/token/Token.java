package zas.admin.zec.backend.rag.token;

import java.util.Map;

public sealed interface Token permits SourceToken, StatusToken, SuggestionToken, TextToken, WorkspaceToken {
    String content();
    Map<String, String> metadata();
}
