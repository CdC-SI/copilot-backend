package zas.admin.zec.backend.actions.converse;

import zas.admin.zec.backend.rag.token.SourceToken;

import static zas.admin.zec.backend.rag.token.SourceToken.*;

/**
 * Source affichée sous une réponse et persistée dans {@code chat_history.sources}.
 *
 * @param title titre affiché (processus métier : nom du processus) ; {@code null} pour URL / FILE
 */
public record Source(String documentId, SourceType type, String link, String pageNumber, String subsection,
                     String version, String title) {

    public Source(SourceType type, String link) {
        this(null, type, link, null, null, null, null);
    }

    public Source(String documentId, SourceType type, String link, String pageNumber, String subsection, String version) {
        this(documentId, type, link, pageNumber, subsection, version, null);
    }

    public static Source fromToken(SourceToken token) {
        return switch (token.type()) {
            case URL -> new Source(
                    token.documentId(),
                    SourceType.URL,
                    token.metadata().get(KEY_URL),
                    token.metadata().get(KEY_PAGE_NUMBER),
                    token.metadata().get(KEY_SUBSECTION),
                    token.metadata().get(KEY_VERSION)
            );
            case FILE -> new Source(
                    token.documentId(),
                    SourceType.FILE,
                    token.metadata().get(KEY_FILENAME),
                    token.metadata().get(KEY_PAGE_NUMBER),
                    token.metadata().get(KEY_SUBSECTION),
                    token.metadata().get(KEY_VERSION)
            );
            case BUSINESS_PROCESS -> new Source(
                    token.documentId(),
                    SourceType.BUSINESS_PROCESS,
                    token.metadata().get(KEY_URL),
                    null,
                    null,
                    token.metadata().get(KEY_VERSION),
                    token.metadata().get(KEY_TITLE)
            );
        };
    }
}
