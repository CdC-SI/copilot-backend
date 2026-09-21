package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration de la notification par mail des feedbacks négatifs aux modérateurs.
 * L'adresse d'envoi ({@code from}) est différente par environnement.
 */
@ConfigurationProperties(prefix = "feedback.mail")
public record FeedbackMailProperties(
        boolean enabled,
        @NotNull String from,
        @NotNull List<String> recipients,
        @NotNull String subjectPrefix,
        @NotNull String appBaseUrl) {
}
