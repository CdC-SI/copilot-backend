package zas.admin.zec.backend.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Résilience des appels au modèle de chat interne (B4) : timeouts, nouvelles tentatives avant le premier token,
 * circuit breaker et bascule vers un modèle de repli.
 *
 * @param requestTimeout    durée maximale d'un appel HTTP (flux compris), appliquée par le client OpenAI
 * @param firstTokenTimeout délai maximal avant le premier élément d'un flux ; au-delà, l'appel est retenté
 * @param maxAttempts       nombre total de tentatives sur erreur transitoire (5xx, 408, 409, 429, réseau, timeout),
 *                          uniquement tant qu'aucun token n'a été reçu
 * @param initialBackoff    attente avant la 2e tentative (doublée ensuite, avec gigue)
 * @param maxBackoff        attente maximale entre deux tentatives
 * @param circuitBreaker    circuit breaker du modèle principal
 * @param fallback          modèle de repli (optionnel), utilisé si les tentatives sont épuisées ou le circuit ouvert
 */
@Validated
@ConfigurationProperties(prefix = "zia.llm.resilience")
public record LlmResilienceProperties(
        @NotNull @DefaultValue("110s") Duration requestTimeout,
        @NotNull @DefaultValue("30s") Duration firstTokenTimeout,
        @Min(1) @Max(5) @DefaultValue("3") int maxAttempts,
        @NotNull @DefaultValue("500ms") Duration initialBackoff,
        @NotNull @DefaultValue("4s") Duration maxBackoff,
        @Valid @NotNull @DefaultValue CircuitBreaker circuitBreaker,
        @Valid @NotNull @DefaultValue Fallback fallback) {

    /**
     * @param failureRateThreshold    pourcentage d'échecs (erreurs transitoires) qui ouvre le circuit
     * @param slidingWindowSize       nombre d'appels pris en compte
     * @param minimumNumberOfCalls    nombre d'appels minimal avant de calculer le taux d'échec
     * @param waitDurationInOpenState durée d'ouverture avant de laisser passer des appels de test
     */
    public record CircuitBreaker(
            @Min(1) @Max(100) @DefaultValue("50") int failureRateThreshold,
            @Min(1) @DefaultValue("20") int slidingWindowSize,
            @Min(1) @DefaultValue("10") int minimumNumberOfCalls,
            @NotNull @DefaultValue("30s") Duration waitDurationInOpenState) {}

    /**
     * Modèle de repli compatible OpenAI. Désactivé tant que {@code chatModel} ou {@code chatBaseUrl} est vide.
     *
     * @param chatModel       nom du modèle
     * @param chatBaseUrl     URL de base (sans {@code /v1})
     * @param apiKey          clé d'API
     * @param reasoningEffort effort de raisonnement ; vide si le modèle ne le supporte pas
     */
    public record Fallback(String chatModel, String chatBaseUrl, String apiKey, String reasoningEffort) {

        public boolean enabled() {
            return chatModel != null && !chatModel.isBlank() && chatBaseUrl != null && !chatBaseUrl.isBlank();
        }
    }
}
