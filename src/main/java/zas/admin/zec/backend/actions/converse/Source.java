package zas.admin.zec.backend.actions.converse;

import zas.admin.zec.backend.rag.token.SourceToken;

import static zas.admin.zec.backend.rag.token.SourceToken.KEY_FILENAME;
import static zas.admin.zec.backend.rag.token.SourceToken.KEY_URL;

public record Source(SourceType type, String link) {
    public static Source fromToken(SourceToken token) {
        return switch (token.type()) {
            case URL -> new Source(SourceType.URL, token.metadata().get(KEY_URL));
            case FILE -> new Source(SourceType.FILE, token.metadata().get(KEY_FILENAME));
        };
    }
}
