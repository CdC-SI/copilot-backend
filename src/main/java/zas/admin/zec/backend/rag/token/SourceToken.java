package zas.admin.zec.backend.rag.token;

import zas.admin.zec.backend.actions.converse.SourceType;

import java.util.Map;

public final class SourceToken implements Token {

    public static final String KEY_URL = "url";
    public static final String KEY_FILENAME = "filename";

    private final SourceType type;
    private final String url;
    private final String filename;

    private SourceToken(SourceType type, String url, String filename) {
        this.type = type;
        this.url = url;
        this.filename = filename;
    }

    public static SourceToken fromURL(String url) {
        return new SourceToken(SourceType.URL, url, null);
    }

    public static SourceToken fromFile(String filename) {
        return new SourceToken(SourceType.FILE, null, filename);
    }

    @Override
    public String content() {
        var fUrl = url != null ? url : "";
        var fFilename = filename != null ? filename : "";
        return "<source><url>%s</url><file>%s</file></source>".formatted(fUrl, fFilename);
    }

    @Override
    public Map<String, String> metadata() {
        var fUrl = url != null ? url : "";
        var fFilename = filename != null ? filename : "";
        return Map.of(KEY_URL, fUrl, KEY_FILENAME, fFilename);
    }

    public SourceType type() {
        return type;
    }
}
