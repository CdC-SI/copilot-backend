package zas.admin.zec.backend.rag.token;

import java.time.Instant;
import java.util.Map;

/**
 * Demande d'approbation d'un appel de tool (T1) : l'exécution est suspendue jusqu'à la décision de l'utilisateur,
 * l'expiration ({@code expiresAt}) ou l'arrêt de la génération.
 *
 * @param callId     identifiant de l'appel ({@link ToolCallToken#callId()})
 * @param approvalId identifiant de la demande, à fournir à l'endpoint de décision
 * @param tool       nom du tool
 * @param arguments  arguments fournis par le LLM, tronqués pour l'affichage
 * @param expiresAt  instant au-delà duquel la demande est refusée d'office
 */
public record ApprovalRequiredToken(String callId, String approvalId, String tool, Map<String, Object> arguments,
                                    Instant expiresAt) implements Token {

    @Override
    public String content() {
        return "";
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
