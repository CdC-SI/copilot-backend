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
    /** Numéro de citation affiché dans la réponse ({@code [^n]}, F2a) ; {@code null} si la source n'est pas citée. */
    private final Integer citation;

    private SourceToken(String documentId, SourceType type, String url, String filename,
                        String pageNumber, String subsection, String version, String title) {
        this(documentId, type, url, filename, toRange(pageNumber), subsection, version, title, null);
    }

    private SourceToken(String documentId, SourceType type, String url, String filename,
                        String pageNumber, String subsection, String version, String title, Integer citation) {

        this.documentId = documentId;
        this.type = type;
        this.url = url != null ? url : "";
        this.filename = filename != null ? filename : "";
        this.pageNumber = pageNumber != null ? pageNumber : "";
        this.subsection = subsection != null ? subsection : "";
        this.version = version != null ? version : "";
        this.title = title != null ? title : "";
        this.citation = citation;
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

    public Integer citation() {
        return citation;
    }

    /** Copie numérotée ({@code [^n]} dans la réponse). */
    public SourceToken withCitation(int number) {
        return new SourceToken(documentId, type, url, filename, pageNumber, subsection, version, title, number);
    }

    /**
     * Clé d'affichage : deux sources de même clé apparaissent identiques à l'écran (type, lien, page, section, version,
     * titre) ; elles sont fusionnées et partagent un même numéro de citation.
     */
    public String displayKey() {
        return String.join("|", type.name(), url, filename, pageNumber, subsection, version, title);
    }

    private static String toRange(String pageNumber) {
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
