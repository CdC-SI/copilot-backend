package zas.admin.zec.backend.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.config.properties.LlmPricingProperties;
import zas.admin.zec.backend.rag.TurnUsage;

import java.util.List;

/**
 * Métriques d'usage LLM par tour, à partir de {@link TurnUsage} :
 * <ul>
 *   <li>{@value #TOKENS} : tokens, tags {@code model}, {@code type} ({@code prompt} = entrée totale, {@code cached} =
 *   part de l'entrée servie par le cache, {@code completion}), {@code call} ({@code agent} / {@code auxiliary}),
 *   {@code purpose} et {@code workspace} ;</li>
 *   <li>{@value #COST} : coût estimé, si un prix est configuré pour le modèle ({@code zia.llm.pricing}).</li>
 * </ul>
 * Pas de tag utilisateur (cardinalité) : l'usage par utilisateur reste requêtable via {@code chat_history.usage}.
 * Complète la métrique native {@code gen_ai.client.token.usage} de Spring AI (par modèle uniquement).
 */
@Slf4j
@Component
@EnableConfigurationProperties(LlmPricingProperties.class)
public class LlmUsageMetrics {

    static final String TOKENS = "zia.llm.tokens";
    static final String COST = "zia.llm.cost";
    static final String UNKNOWN = "unknown";
    static final String NONE = "none";

    private final MeterRegistry meterRegistry;
    private final LlmPricingProperties pricing;

    public LlmUsageMetrics(MeterRegistry meterRegistry, LlmPricingProperties pricing) {
        this.meterRegistry = meterRegistry;
        this.pricing = pricing;
    }

    @EventListener
    public void onTurnUsage(TurnUsageEvent event) {
        try {
            record(event.usage(), event.workspace());
        } catch (RuntimeException e) {
            log.warn("Enregistrement des métriques d'usage LLM impossible", e);
        }
    }

    void record(TurnUsage usage, String workspace) {
        if (usage == null) {
            return;
        }
        String workspaceTag = workspace != null && !workspace.isBlank() ? workspace : NONE;
        record(usage.agentCalls(), "agent", workspaceTag);
        record(usage.auxiliaryCalls(), "auxiliary", workspaceTag);
    }

    private void record(List<TurnUsage.LlmCall> calls, String callKind, String workspace) {
        for (var call : calls) {
            String model = call.model() != null ? call.model() : UNKNOWN;
            Tags tags = Tags.of("model", model, "call", callKind,
                    "purpose", call.purpose() != null ? call.purpose() : UNKNOWN, "workspace", workspace);
            long cached = call.cachedTokens() != null ? call.cachedTokens() : 0;
            increment(tags.and("type", "prompt"), call.promptTokens());
            increment(tags.and("type", "completion"), call.completionTokens());
            if (call.cachedTokens() != null) {
                increment(tags.and("type", "cached"), cached);
            }
            var price = pricing.models().get(model);
            if (price != null) {
                Counter.builder(COST)
                        .description("Coût estimé des appels LLM")
                        .baseUnit(pricing.currency())
                        .tags(Tags.of("model", model, "call", callKind, "workspace", workspace))
                        .register(meterRegistry)
                        .increment(price.cost(call.promptTokens(), cached, call.completionTokens()));
            }
        }
    }

    private void increment(Tags tags, long tokens) {
        Counter.builder(TOKENS)
                .description("Tokens consommés par les appels LLM d'un tour")
                .baseUnit("tokens")
                .tags(tags)
                .register(meterRegistry)
                .increment(tokens);
    }
}
