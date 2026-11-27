package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Approbation humaine des appels de tools (T1).
 *
 * @param requiredTools noms des tools dont chaque appel doit être approuvé par l'utilisateur (aucun par défaut)
 * @param timeout       délai d'attente de la décision ; au-delà, l'appel est refusé d'office. Doit rester nettement
 *                      inférieur à {@code spring.mvc.async.request-timeout}, qui borne toute la réponse en streaming ;
 *                      la connexion reste ouverte pendant l'attente grâce au heartbeat
 *                      ({@link ConversationStreamProperties#heartbeatInterval()}).
 */
@Validated
@ConfigurationProperties(prefix = "zia.tools.approval")
public record ToolApprovalProperties(List<String> requiredTools, @NotNull Duration timeout) {

    public ToolApprovalProperties {
        requiredTools = requiredTools == null ? List.of()
                : requiredTools.stream().map(String::strip).filter(name -> !name.isEmpty()).toList();
    }
}
