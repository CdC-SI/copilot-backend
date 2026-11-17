package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Mémoire conversationnelle (B2) : fenêtre de l'historique envoyé au LLM et résumé des tours plus anciens.
 *
 * @param historyTokenBudget budget (estimé) de l'historique envoyé au LLM, résumé compris ; les tours les plus anciens
 *                           au-delà sont retirés du contexte
 * @param compactionRatio    part du budget conservée en tours complets après la génération d'un résumé (hystérésis :
 *                           le résumé n'est régénéré qu'une fois le budget de nouveau dépassé)
 * @param summaryEnabled     résumé des tours retirés ; sinon simple fenêtre glissante
 */
@ConfigurationProperties(prefix = "zia.memory")
public record MemoryProperties(@DefaultValue("12000") int historyTokenBudget,
                               @DefaultValue("0.6") double compactionRatio,
                               @DefaultValue("true") boolean summaryEnabled) {

    public MemoryProperties {
        if (historyTokenBudget <= 0) {
            throw new IllegalArgumentException("zia.memory.history-token-budget doit être positif");
        }
        if (compactionRatio <= 0 || compactionRatio >= 1) {
            throw new IllegalArgumentException("zia.memory.compaction-ratio doit être compris entre 0 et 1 (exclus)");
        }
    }

    /** Taille cible (en tokens) des tours conservés après compaction. */
    public int compactionTarget() {
        return (int) (historyTokenBudget * compactionRatio);
    }
}
