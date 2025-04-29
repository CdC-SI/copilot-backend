package zas.admin.zec.backend.rag.token;

import java.util.Map;

public sealed interface Token permits TextToken, StatusToken, SourceToken {
    String content();
    Map<String, String> metadata();
}
