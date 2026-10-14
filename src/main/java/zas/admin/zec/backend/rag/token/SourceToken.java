package zas.admin.zec.backend.rag.token;

import zas.admin.zec.backend.actions.converse.SourceType;

import java.util.Map;

/**
 * Source affichée sous la réponse. Format SSE :
 * {@code <source><id/><url/><file/><pn/><sub/><v/><type/><t/></source>}.
 * Les balises {@code <type>} (URL / FILE / BUSINESS_PROCESS) et {@code <t>} (titre) sont optionnelles
 * côté frontend (rétrocompatibilité).
 */
public final class SourceToken implements Token {

    public static final String KEY_URL = "url";
    public static final String KEY_FILENAME = "filename";
    public static final String KEY_PAGE_NUMBER = "pageNumber";
    public static final String KEY_SUBSECTION = "subsection";
    public static final String KEY_VERSION = "version";
    public static final String KEY_TITLE = "title";

    private final String documentId;
    private final SourceType type;
    private final String url;
    private final String filename;
    private final String pageNumber;
    private final String subsection;
    private final String version;
    private final String title;

    private SourceToken(String documentId, SourceType type, String url, String filename,
                        String pageNumber, String subsection, String version, String title) {

        this.documentId = documentId;
        this.type = type;
        this.url = url != null ? url : "";
        this.filename = filename != null ? filename : "";
        this.pageNumber = toRange(pageNumber);
        this.subsection = subsection != null ? subsection : "";
        this.version = version != null ? version : "";
        this.title = title != null ? title : "";
    }

    public static SourceToken fromURL(String url) {
        return new SourceToken(null, SourceType.URL, url, null, null, null, null, null);
    }

    public static SourceToken fromURLWithDetails(String documentId, String url, String pageNumber, String subsection, String version) {
        return new SourceToken(documentId, SourceType.URL, url, null, pageNumber, subsection, version, null);
    }

    public static SourceToken fromFile(String filename) {
        return new SourceToken(null, SourceType.FILE, null, filename, null, null, null, null);
    }

    public static SourceToken fromFileWithDetails(String documentId, String filename, String pageNumber, String subsection, String version) {
        return new SourceToken(documentId, SourceType.FILE, null, filename, pageNumber, subsection, version, null);
    }

    /**
     * Processus métier : titre = nom du processus, lien = lien BPanda, version = version BPanda.
     */
    public static SourceToken fromBusinessProcess(String documentId, String processName, String link, String version) {
        return new SourceToken(documentId, SourceType.BUSINESS_PROCESS, link, null, null, null, version, processName);
    }

    @Override
    public String content() {
        return "<source><id>%s</id><url>%s</url><file>%s</file><pn>%s</pn><sub>%s</sub><v>%s</v><type>%s</type><t>%s</t></source>"
                .formatted(documentId, url, filename, pageNumber, subsection, version, type.name(), title);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of(
                KEY_URL, url,
                KEY_FILENAME, filename,
                KEY_PAGE_NUMBER, pageNumber,
                KEY_SUBSECTION, subsection,
                KEY_VERSION, version,
                KEY_TITLE, title
        );
    }

    public SourceType type() {
        return type;
    }

    public String documentId() {
        return documentId;
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
