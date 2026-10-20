package zas.admin.zec.backend.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tokens consommés par un tour, persistés dans {@code chat_history.usage} (jsonb) : appels LLM de la boucle agentique
 * (un par étape) et appels auxiliaires (ex. inférence du workspace dans RAGTool).
 *
 * <p>{@code cachedTokens} n'est renseigné que si le serveur le fournit ({@code prompt_tokens_details.cached_tokens} ;
 * vLLM : option {@code --enable-prompt-tokens-details}).</p>
 */
public record TurnUsage(List<LlmCall> agentCalls, List<LlmCall> auxiliaryCalls) {

    /** Un appel LLM. */
    public record LlmCall(String purpose, long promptTokens, long completionTokens, Long cachedTokens) {

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("purpose", purpose);
            map.put("prompt_tokens", promptTokens);
            map.put("completion_tokens", completionTokens);
            map.put("cached_tokens", cachedTokens);
            return map;
        }
    }

    public TurnUsage {
        agentCalls = agentCalls != null ? List.copyOf(agentCalls) : List.of();
        auxiliaryCalls = auxiliaryCalls != null ? List.copyOf(auxiliaryCalls) : List.of();
    }

    public Map<String, Object> toMap() {
        List<LlmCall> all = new java.util.ArrayList<>(agentCalls);
        all.addAll(auxiliaryCalls);
        boolean cachedReported = all.stream().anyMatch(c -> c.cachedTokens() != null);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("llm_calls", all.size());
        map.put("prompt_tokens", all.stream().mapToLong(LlmCall::promptTokens).sum());
        map.put("completion_tokens", all.stream().mapToLong(LlmCall::completionTokens).sum());
        map.put("cached_tokens", cachedReported
                ? all.stream().map(LlmCall::cachedTokens).filter(Objects::nonNull).mapToLong(Long::longValue).sum()
                : null);
        map.put("agent_calls", agentCalls.stream().map(LlmCall::toMap).toList());
        map.put("auxiliary_calls", auxiliaryCalls.stream().map(LlmCall::toMap).toList());
        return map;
    }
}
