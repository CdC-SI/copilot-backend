package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Budget d'appels de tools par tour, partagé par tous les tools via le {@code ToolContext}
 * ({@link ToolContextKeys#CTX_TOOL_CALL_BUDGET}). Spring AI ne borne pas nativement la boucle de
 * tool-calling : une fois le budget épuisé, les tools ne s'exécutent plus et retournent
 * {@link #EXHAUSTED_MESSAGE}, invitant le LLM à répondre avec les informations déjà collectées.
 */
@Slf4j
public final class ToolCallBudget {

    public static final String EXHAUSTED_MESSAGE = """
            <tool_call_limit_reached>
            The maximum number of tool calls for this turn has been reached. This call was NOT executed.
            Do not call any more tools. Answer now using only the information already retrieved, \
            or tell the user that the available documentation is insufficient.
            </tool_call_limit_reached>
            """;

    private ToolCallBudget() {}

    /**
     * Consomme une unité du budget.
     *
     * @return {@code true} si l'appel est autorisé (budget restant, ou absent du contexte),
     * {@code false} si le budget est épuisé.
     */
    public static boolean tryAcquire(Map<String, Object> context, String toolName) {
        if (!(context.get(ToolContextKeys.CTX_TOOL_CALL_BUDGET) instanceof AtomicInteger budget)) {
            return true;
        }
        boolean allowed = budget.getAndDecrement() > 0;
        if (!allowed) {
            log.warn("Budget d'appels de tools épuisé : appel à '{}' refusé (conversation {})",
                    toolName, context.get(ToolContextKeys.CTX_CONVERSATION_ID));
        }
        return allowed;
    }
}
