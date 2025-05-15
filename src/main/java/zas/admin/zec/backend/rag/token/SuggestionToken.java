package zas.admin.zec.backend.rag.token;

import java.util.Map;

public final class SuggestionToken implements Token {
    private final String suggestion;

    public SuggestionToken(String suggestion) {
        this.suggestion = suggestion;
    }

    public String suggestion() {
        return suggestion;
    }

    @Override
    public String content() {
        return "<suggestion>%s</suggestion>".formatted(suggestion);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
