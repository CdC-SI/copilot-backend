package zas.admin.zec.backend.rag.token;

import java.util.Map;

public sealed interface Token permits ApprovalRequiredToken, SourceToken, StatusToken, SuggestionToken, TextToken,
        ToolCallToken, ToolResultToken, TurnTraceToken, WorkspaceToken {
    String content();
    Map<String, String> metadata();
}
