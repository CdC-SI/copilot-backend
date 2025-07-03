package zas.admin.zec.backend.actions.converse;

import zas.admin.zec.backend.rag.token.SourceToken;

import static zas.admin.zec.backend.rag.token.SourceToken.*;

public record Source(SourceType type, String link, String pageNumber, String subsection, String version) {
    public Source(SourceType type, String link) {
        this(type, link, null, null, null);
    }

    public static Source fromToken(SourceToken token) {
        return switch (token.type()) {
            case URL -> new Source(
                    SourceType.URL,
                    token.metadata().get(KEY_URL),
                    token.metadata().get(KEY_PAGE_NUMBER),
                    token.metadata().get(KEY_SUBSECTION),
                    token.metadata().get(KEY_VERSION)
            );
            case FILE -> new Source(
                    SourceType.FILE,
                    token.metadata().get(KEY_FILENAME),
                    token.metadata().get(KEY_PAGE_NUMBER),
                    token.metadata().get(KEY_SUBSECTION),
                    token.metadata().get(KEY_VERSION)
            );
        };
    }
}
