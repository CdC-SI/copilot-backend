package zas.admin.zec.backend.rag.token;

import java.util.Map;

public final class SourceToken implements Token {

    private final String url;

    public SourceToken(String url) {
        this.url = url;
    }

    @Override
    public String content() {
        return "<source><a href='%s'>%s</a></source>".formatted(url, url);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of("url", url);
    }
}
