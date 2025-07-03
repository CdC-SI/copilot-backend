package zas.admin.zec.backend.rag.token;

import zas.admin.zec.backend.actions.converse.SourceType;

import java.util.Map;

public final class SourceToken implements Token {

    public static final String KEY_URL = "url";
    public static final String KEY_FILENAME = "filename";
    public static final String KEY_PAGE_NUMBER = "pageNumber";
    public static final String KEY_SUBSECTION = "subsection";
    public static final String KEY_VERSION = "version";

    private final SourceType type;
    private final String url;
    private final String filename;
    private final String pageNumber;
    private final String subsection;
    private final String version;

    private SourceToken(SourceType type, String url, String filename,
                        String pageNumber, String subsection, String version) {

        this.type = type;
        this.url = url != null ? url : "";
        this.filename = filename != null ? filename : "";
        this.pageNumber = toRange(pageNumber);
        this.subsection = subsection != null ? subsection : "";
        this.version = version != null ? version : "";
    }

    public static SourceToken fromURL(String url) {
        return new SourceToken(SourceType.URL, url, null, null, null, null);
    }

    public static SourceToken fromURLWithDetails(String url, String pageNumber, String subsection, String version) {
        return new SourceToken(SourceType.URL, url, null, pageNumber, subsection, version);
    }

    public static SourceToken fromFile(String filename) {
        return new SourceToken(SourceType.FILE, null, filename, null, null, null);
    }

    public static SourceToken fromFileWithDetails(String filename, String pageNumber, String subsection, String version) {
        return new SourceToken(SourceType.FILE, null, filename, pageNumber, subsection, version);
    }

    @Override
    public String content() {
        return "<source><url>%s</url><file>%s</file><pn>%s</pn><sub>%s</sub><v>%s</v></source>"
                .formatted(url, filename, pageNumber, subsection, version);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of(
                KEY_URL, url,
                KEY_FILENAME, filename,
                KEY_PAGE_NUMBER, pageNumber,
                KEY_SUBSECTION, subsection,
                KEY_VERSION, version
        );
    }

    public SourceType type() {
        return type;
    }

    private String toRange(String pageNumber) {
        if (pageNumber == null || pageNumber.isEmpty()) {
            return "";
        }
        String[] parts = pageNumber.split(",");
        if (parts.length == 1) {
            return parts[0];
        } else if (parts.length == 2) {
            return parts[0] + "," + parts[1];
        } else {
            return parts[0] + "-" + parts[parts.length - 1];
        }
    }
}
