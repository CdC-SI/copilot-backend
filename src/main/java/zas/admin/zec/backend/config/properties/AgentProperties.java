package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages de l'appel agent (boucle agentique de {@code RAGChatService}) : appliqués à cet appel seulement, les appels
 * internes (choix de l'espace, réécriture de requête) gardent les réglages du modèle ({@code ChatModelsConfig}).
 *
 * @param reasoningEffort effort de raisonnement du modèle, une des valeurs acceptées par le serveur ({@code none},
 *                        {@code minimal}, {@code low}, {@code medium}, {@code high}, {@code xhigh}, {@code max}) :
 *                        une autre valeur, par exemple suivie d'un commentaire dans le {@code .env}, bloque le
 *                        démarrage au lieu de faire échouer chaque question
 * @param temperature     température de génération
 */
@Validated
@ConfigurationProperties(prefix = "rag.agent")
public record AgentProperties(
        @NotBlank @Pattern(regexp = "none|minimal|low|medium|high|xhigh|max") String reasoningEffort,
        @NotNull @DecimalMin("0.0") @DecimalMax("2.0") Double temperature) {}
