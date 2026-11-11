package zas.admin.zec.backend.actions.analyze;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import zas.admin.zec.backend.actions.rate.FeedbackNotificationService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Planifie dynamiquement l'envoi du rapport hebdomadaire des feedbacks : la configuration
 * (activation, expression cron, fuseau horaire, fenêtre d'analyse) est relue en base à chaque
 * calcul de la prochaine échéance, ce qui permet de la modifier à chaud via l'API sans
 * redémarrer l'application. Si la tâche est désactivée, {@link Trigger#nextExecution} renvoie
 * {@code null} : le scheduler met alors la tâche en pause jusqu'à la prochaine réévaluation.
 */
@Slf4j
@Configuration
public class FeedbackReportScheduler implements SchedulingConfigurer {

    private final FeedbackReportConfigService configService;
    private final FeedbackReportService reportService;
    private final FeedbackNotificationService notificationService;

    public FeedbackReportScheduler(FeedbackReportConfigService configService,
                                    FeedbackReportService reportService,
                                    FeedbackNotificationService notificationService) {
        this.configService = configService;
        this.reportService = reportService;
        this.notificationService = notificationService;
    }

    @Bean
    public ThreadPoolTaskScheduler feedbackReportTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("feedback-report-");
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(feedbackReportTaskScheduler());
        taskRegistrar.addTriggerTask(this::runWeeklyReport, this::nextExecution);
    }

    private Instant nextExecution(TriggerContext triggerContext) {
        var config = configService.get();
        if (!config.enabled()) {
            log.debug("Rapport hebdomadaire des feedbacks désactivé, aucune échéance planifiée");
            return null;
        }
        if (!CronExpression.isValidExpression(config.cronExpression())) {
            log.warn("Expression cron invalide pour le rapport hebdomadaire des feedbacks : {}", config.cronExpression());
            return null;
        }

        var zoneId = ZoneId.of(config.zoneId());
        var lastExecution = triggerContext.lastActualExecution();
        ZonedDateTime base = lastExecution != null
                ? ZonedDateTime.ofInstant(lastExecution, zoneId)
                : ZonedDateTime.now(zoneId);

        var next = CronExpression.parse(config.cronExpression())
                .next(base.toLocalDateTime());

        return next == null ? null : next.atZone(zoneId).toInstant();
    }

    private void runWeeklyReport() {
        var config = configService.get();
        if (!config.enabled()) {
            return;
        }

        log.info("Génération du rapport hebdomadaire des feedbacks (fenêtre de {} jour(s))", config.lookbackDays());
        var end = LocalDateTime.now();
        var start = end.minusDays(config.lookbackDays());
        var report = reportService.buildReport(start, end);
        notificationService.sendWeeklyReport(report, config.recipients());
    }
}
