package zas.admin.zec.backend.rag.token;

import zas.admin.zec.backend.rag.TurnTrace;

import java.util.Map;

/**
 * Token <b>interne</b> (jamais transmis au frontend) émis en fin de réponse par l'agent : trace du tour
 * (statut déclaré + documents consultés), persistée avec le message assistant.
 */
public final class TurnTraceToken implements Token {

    private final TurnTrace trace;

    public TurnTraceToken(TurnTrace trace) {
        this.trace = trace != null ? trace : TurnTrace.empty();
    }

    public TurnTrace trace() {
        return trace;
    }

    @Override
    public String content() {
        return "";
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
