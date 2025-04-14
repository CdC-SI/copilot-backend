package zas.admin.zec.backend.rag.token;

import java.util.Map;

public final class TextToken implements Token {
    private final String text;

    public TextToken(String text) {
        this.text = text != null ? text : "";
    }

    @Override
    public String content() {
        return text;
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
