package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * Prix des modèles LLM, pour la métrique {@code zia.llm.cost}. Optionnel : sans prix pour un modèle, seul le nombre de
 * tokens est mesuré.
 *
 * @param currency unité monétaire (documentaire, ajoutée en tag)
 * @param models   prix par nom de modèle (tel que renvoyé par le serveur)
 */
@ConfigurationProperties(prefix = "zia.llm.pricing")
public record LlmPricingProperties(@DefaultValue("CHF") String currency, Map<String, Price> models) {

    public LlmPricingProperties {
        models = models != null ? Map.copyOf(models) : Map.of();
    }

    /**
     * Prix par million de tokens.
     *
     * @param input       tokens d'entrée non servis par le cache
     * @param cachedInput tokens d'entrée servis par le cache (prix d'entrée par défaut)
     * @param output      tokens générés
     */
    public record Price(double input, Double cachedInput, double output) {

        public double cost(long promptTokens, long cachedTokens, long completionTokens) {
            long cached = Math.min(Math.max(cachedTokens, 0), promptTokens);
            double cachedPrice = cachedInput != null ? cachedInput : input;
            return ((promptTokens - cached) * input + cached * cachedPrice + completionTokens * output) / 1_000_000d;
        }
    }
}
