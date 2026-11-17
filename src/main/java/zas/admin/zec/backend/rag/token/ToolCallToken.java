package zas.admin.zec.backend.rag.token;

import java.util.Map;

/**
 * Début d'un appel de tool par l'agent (T1), émis avant son exécution (et avant une éventuelle demande d'approbation).
 *
 * @param callId    identifiant de l'appel, unique dans le tour ; repris par {@link ToolResultToken}
 * @param tool      nom du tool
 * @param arguments arguments fournis par le LLM, tronqués pour l'affichage
 */
public record ToolCallToken(String callId, String tool, Map<String, Object> arguments) implements Token {

    @Override
    public String content() {
        return "";
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
