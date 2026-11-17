package zas.admin.zec.backend.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Usage par appel LLM relevé dans le flux (un chunk d'usage par appel, usage vide ailleurs). */
class UsageTrackerTest {

    private static ChatResponse chunk(Usage usage) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("x"))),
                ChatResponseMetadata.builder().usage(usage).build());
    }

    @Test
    void eachNonEmptyUsageIsOneCall() {
        var tracker = new UsageTracker();
        tracker.accept(chunk(new EmptyUsage()));
        tracker.accept(chunk(new DefaultUsage(1200, 40)));
        tracker.accept(chunk(new DefaultUsage(1200, 40)));   // répété : même appel
        tracker.accept(chunk(new EmptyUsage()));
        tracker.accept(chunk(new DefaultUsage(4300, 350, 4650, null, 1100L, null)));
        tracker.accept(null);

        var calls = tracker.agentCalls();
        assertEquals(2, calls.size());
        assertEquals(new TurnUsage.LlmCall("agent", 1200, 40, null), calls.get(0));
        assertEquals(new TurnUsage.LlmCall("agent", 4300, 350, 1100L), calls.get(1));
    }

    @Test
    void recordsTheModelThatAnswered() {
        var tracker = new UsageTracker();
        tracker.accept(new ChatResponse(List.of(new Generation(new AssistantMessage("x"))),
                ChatResponseMetadata.builder().model("fallback-model").usage(new DefaultUsage(10, 2)).build()));

        assertEquals("fallback-model", tracker.agentCalls().get(0).model());
        assertEquals("fallback-model", new TurnUsage(tracker.agentCalls(), List.of()).toMap()
                .get("agent_calls") instanceof List<?> calls ? ((Map<?, ?>) calls.get(0)).get("model") : null);
    }

    @Test
    void turnUsageSumsAgentAndAuxiliaryCalls() {
        var usage = new TurnUsage(
                List.of(new TurnUsage.LlmCall("agent", 1000, 50, null), new TurnUsage.LlmCall("agent", 3000, 200, null)),
                List.of(new TurnUsage.LlmCall("workspace_inference", 500, 5, null)));
        Map<String, Object> map = usage.toMap();

        assertEquals(3, map.get("llm_calls"));
        assertEquals(4500L, map.get("prompt_tokens"));
        assertEquals(255L, map.get("completion_tokens"));
        assertNull(map.get("cached_tokens"));
        assertEquals(2, ((List<?>) map.get("agent_calls")).size());

        var withCache = new TurnUsage(List.of(new TurnUsage.LlmCall("agent", 1000, 50, 600L)), List.of()).toMap();
        assertEquals(600L, withCache.get("cached_tokens"));
    }
}
