package zas.admin.zec.backend.rag;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Relève l'usage (tokens) des réponses streamées d'un tour. Avec {@code streamUsage(true)}, chaque appel LLM de la
 * boucle de tool-calling se termine par un chunk portant l'usage de cet appel ; les autres chunks portent un usage
 * vide. En streaming, Spring AI 2.0 ne cumule pas l'usage d'un appel à l'autre ({@code OpenAiChatModel.internalStream}
 * appelle {@code UsageCalculator.getCumulativeUsage(usage, null)}) : chaque usage non vide correspond à un appel.
 *
 * <p>Non thread-safe : une instance par tour, alimentée séquentiellement par le flux.</p>
 */
public final class UsageTracker {

    private final List<TurnUsage.LlmCall> calls = new ArrayList<>();
    private Usage last;

    public void accept(ChatResponse response) {
        Usage usage = response != null && response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        if (usage == null || usage.getPromptTokens() == null || usage.getPromptTokens() <= 0 || usage.equals(last)) {
            return;
        }
        last = usage;
        calls.add(new TurnUsage.LlmCall("agent", usage.getPromptTokens(),
                usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0, usage.getCacheReadInputTokens()));
    }

    /** Usage de chaque appel de la boucle agentique, dans l'ordre. */
    public List<TurnUsage.LlmCall> agentCalls() {
        return List.copyOf(calls);
    }
}
