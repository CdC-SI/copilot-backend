package zas.admin.zec.backend.rag;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

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

    /** Un appel LLM ; {@code model} : modèle ayant répondu (primaire ou repli), {@code null} s'il est inconnu. */
    public record LlmCall(String purpose, long promptTokens, long completionTokens, Long cachedTokens, String model) {

        public LlmCall(String purpose, long promptTokens, long completionTokens, Long cachedTokens) {
            this(purpose, promptTokens, completionTokens, cachedTokens, null);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("purpose", purpose);
            map.put("model", model);
            map.put("prompt_tokens", promptTokens);
            map.put("completion_tokens", completionTokens);
            map.put("cached_tokens", cachedTokens);
            return map;
        }

        /** Usage d'un appel à partir de la réponse du modèle, ou {@code null} si elle ne porte pas d'usage. */
        public static LlmCall from(String purpose, ChatResponse response) {
            ChatResponseMetadata metadata = response != null ? response.getMetadata() : null;
            Usage usage = metadata != null ? metadata.getUsage() : null;
            if (usage == null || usage.getPromptTokens() == null) {
                return null;
            }
            String model = metadata.getModel() != null && !metadata.getModel().isBlank() ? metadata.getModel() : null;
            return new LlmCall(purpose, usage.getPromptTokens(),
                    usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0,
                    usage.getCacheReadInputTokens(), model);
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
