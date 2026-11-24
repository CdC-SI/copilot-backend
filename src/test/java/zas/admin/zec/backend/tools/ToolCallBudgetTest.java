package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Budget d'appels de tools partagé par les tools d'un tour. */
class ToolCallBudgetTest {

    @Test
    void withoutBudgetInTheContextEveryCallIsAllowed() {
        assertTrue(ToolCallBudget.tryAcquire(Map.of(), "search"));
    }

    @Test
    void callsAreRefusedOnceTheSharedBudgetIsExhausted() {
        var budget = new AtomicInteger(2);
        Map<String, Object> context = Map.of(ToolContextKeys.CTX_TOOL_CALL_BUDGET, budget,
                ToolContextKeys.CTX_CONVERSATION_ID, "conv");

        assertTrue(ToolCallBudget.tryAcquire(context, "search"));
        assertTrue(ToolCallBudget.tryAcquire(context, "get_documents"));
        assertFalse(ToolCallBudget.tryAcquire(context, "search"));
        assertFalse(ToolCallBudget.tryAcquire(context, "get_documents"));
    }
}
