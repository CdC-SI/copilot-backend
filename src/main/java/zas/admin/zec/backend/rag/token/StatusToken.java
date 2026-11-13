package zas.admin.zec.backend.rag.token;

import zas.admin.zec.backend.rag.ChatStatus;

import java.util.Map;

public final class StatusToken implements Token {
    private final String content;
    private final String tag;

    public StatusToken(ChatStatus status, String lang, Object... args) {
        this.content = status.text(lang).formatted(args);
        this.tag = status.tag();
    }

    @Override
    public String content() {
        return "<%s>%s</%s>".formatted(tag, content, tag);
    }

    /** Identifiant stable de l'étape ({@link ChatStatus#tag()}). */
    public String tag() {
        return tag;
    }

    /** Texte localisé de l'étape. */
    public String label() {
        return content;
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
