package zas.admin.zec.backend.rag.token;

import java.util.Map;

/**
 * Fin d'un appel de tool (T1).
 *
 * @param callId        identifiant de l'appel ({@link ToolCallToken#callId()})
 * @param tool          nom du tool
 * @param status        statut de l'appel ({@code ToolCallRecord.STATUS_*})
 * @param durationMs    durée d'exécution (attente d'approbation exclue)
 * @param documentCount nombre de documents retournés par l'appel
 */
public record ToolResultToken(String callId, String tool, String status, long durationMs, int documentCount)
        implements Token {

    @Override
    public String content() {
        return "";
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
