package zas.admin.zec.backend.rag.token;

import zas.admin.zec.backend.rag.RAGStatus;

import java.util.Map;

public final class StatusToken implements Token {
    private final String content;
    private final String tag;

    public StatusToken(RAGStatus status, String lang, Object... args) {
        this.content = status.text(lang).formatted(args);
        this.tag = status.tag();
    }

    @Override
    public String content() {
        return "<%s>%s</%s>".formatted(tag, content, tag);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
