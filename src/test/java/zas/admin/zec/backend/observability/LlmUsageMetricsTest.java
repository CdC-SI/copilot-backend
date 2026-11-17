package zas.admin.zec.backend.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.LlmPricingProperties;
import zas.admin.zec.backend.rag.TurnUsage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmUsageMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private double tokens(String model, String call, String type, String workspace) {
        var counter = registry.find(LlmUsageMetrics.TOKENS)
                .tags("model", model, "call", call, "type", type, "workspace", workspace).counter();
        return counter != null ? counter.count() : 0;
    }

    @Test
    void countsTokensPerModelCallTypeAndWorkspace() {
        var workspaceService = mock(WorkspaceService.class);
        when(workspaceService.getAllNames()).thenReturn(List.of("AVS"));
        var metrics = new LlmUsageMetrics(registry, new LlmPricingProperties("CHF", null), workspaceService);
        var usage = new TurnUsage(
                List.of(new TurnUsage.LlmCall("agent", 1000, 50, 600L, "qwen"),
                        new TurnUsage.LlmCall("agent", 2000, 100, null, "qwen")),
                List.of(new TurnUsage.LlmCall("workspace_inference", 300, 2, null)));

        metrics.onTurnUsage(new TurnUsageEvent(usage, "AVS"));

        assertEquals(3000, tokens("qwen", "agent", "prompt", "AVS"));
        assertEquals(150, tokens("qwen", "agent", "completion", "AVS"));
        assertEquals(600, tokens("qwen", "agent", "cached", "AVS"));
        assertEquals(300, tokens(LlmUsageMetrics.UNKNOWN, "auxiliary", "prompt", "AVS"));
        assertNull(registry.find(LlmUsageMetrics.TOKENS).tags("call", "auxiliary", "type", "cached").counter(),
                "pas de compteur cached si le serveur ne le fournit pas");
        assertNull(registry.find(LlmUsageMetrics.COST).counter(), "pas de coût sans prix configuré");
    }

    @Test
    void estimatesTheCostWhenTheModelHasAPrice() {
        var pricing = new LlmPricingProperties("CHF",
                Map.of("qwen", new LlmPricingProperties.Price(1.0, 0.1, 4.0)));
        var workspaceService = mock(WorkspaceService.class);
        when(workspaceService.getAllNames()).thenReturn(List.of());
        var metrics = new LlmUsageMetrics(registry, pricing, workspaceService);

        metrics.onTurnUsage(new TurnUsageEvent(new TurnUsage(
                List.of(new TurnUsage.LlmCall("agent", 1_000_000, 500_000, 400_000L, "qwen")), List.of()), null));

        var cost = registry.find(LlmUsageMetrics.COST).tags("model", "qwen", "workspace", LlmUsageMetrics.NONE).counter();
        assertNotNull(cost);
        // 600k non cachés × 1 + 400k cachés × 0.1 + 500k générés × 4, par million.
        assertEquals(0.6 + 0.04 + 2.0, cost.count(), 1e-9);
        assertEquals(1_000_000, tokens("qwen", "agent", "prompt", LlmUsageMetrics.NONE));
    }

    @Test
    void ignoresMissingUsage() {
        var metrics = new LlmUsageMetrics(registry, new LlmPricingProperties(null, null), mock(WorkspaceService.class));
        metrics.onTurnUsage(new TurnUsageEvent(null, "AVS"));
        assertTrue(registry.getMeters().isEmpty());
    }

    @Test
    void mapsUnknownWorkspaceToFixedFallback() {
        var workspaceService = mock(WorkspaceService.class);
        when(workspaceService.getAllNames()).thenReturn(List.of("AVS"));
        var metrics = new LlmUsageMetrics(registry, new LlmPricingProperties("CHF", null), workspaceService);

        metrics.onTurnUsage(new TurnUsageEvent(new TurnUsage(
                List.of(new TurnUsage.LlmCall("agent", 10, 2, null, "qwen")), List.of()), "nonexistent"));

        assertEquals(10, tokens("qwen", "agent", "prompt", LlmUsageMetrics.NONE));
        assertNull(registry.find(LlmUsageMetrics.TOKENS).tags("workspace", "nonexistent").counter());
    }
}
