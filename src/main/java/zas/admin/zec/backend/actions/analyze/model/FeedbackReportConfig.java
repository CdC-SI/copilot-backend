package zas.admin.zec.backend.actions.analyze.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.scheduling.support.CronExpression;

import java.util.List;

/**
 * Réglages de planification du rapport hebdomadaire des feedbacks : fréquence (cron),
 * fuseau horaire, destinataires et fenêtre d'analyse (en jours).
 */
public record FeedbackReportConfig(
        boolean enabled,
        @NotBlank String cronExpression,
        @NotBlank String zoneId,
        @NotEmpty List<String> recipients,
        @Positive Integer lookbackDays) {

    @AssertTrue(message = "L'expression cron est invalide")
    public boolean isCronExpressionValid() {
        return cronExpression == null || CronExpression.isValidExpression(cronExpression);
    }
}
