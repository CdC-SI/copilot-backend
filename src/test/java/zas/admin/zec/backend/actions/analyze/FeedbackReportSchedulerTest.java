package zas.admin.zec.backend.actions.analyze;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.SimpleTriggerContext;
import zas.admin.zec.backend.actions.analyze.FeedbackReportService.FeedbackReport;
import zas.admin.zec.backend.actions.analyze.model.FeedbackReportConfig;
import zas.admin.zec.backend.actions.rate.FeedbackNotificationService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedbackReportSchedulerTest {

    private final FeedbackReportConfigService configService = mock(FeedbackReportConfigService.class);
    private final FeedbackReportService reportService = mock(FeedbackReportService.class);
    private final FeedbackNotificationService notificationService = mock(FeedbackNotificationService.class);
    private TriggerTask task;

    @BeforeEach
    void setUp() {
        var registrar = new ScheduledTaskRegistrar();
        new FeedbackReportScheduler(configService, reportService, notificationService).configureTasks(registrar);
        assertThat(registrar.getScheduler()).isNotNull();
        assertThat(registrar.getTriggerTaskList()).hasSize(1);
        task = registrar.getTriggerTaskList().getFirst();
    }

    @Test
    void nextExecution_disabled_returnsNull() {
        when(configService.get()).thenReturn(config(false, "0 0 8 * * MON"));

        assertThat(task.getTrigger().nextExecution(new SimpleTriggerContext())).isNull();
    }

    @Test
    void nextExecution_invalidCron_returnsNull() {
        when(configService.get()).thenReturn(config(true, "invalide"));

        assertThat(task.getTrigger().nextExecution(new SimpleTriggerContext())).isNull();
    }

    @Test
    void nextExecution_firstRun_isNextCronOccurrenceInConfiguredZone() {
        when(configService.get()).thenReturn(config(true, "0 0 8 * * MON"));

        var next = task.getTrigger().nextExecution(new SimpleTriggerContext());

        var zoned = ZonedDateTime.ofInstant(next, ZoneId.of("Europe/Zurich"));
        assertThat(zoned.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(zoned.getHour()).isEqualTo(8);
        assertThat(next).isAfter(Instant.now()).isBefore(Instant.now().plus(Duration.ofDays(8)));
    }

    @Test
    void nextExecution_afterARun_startsFromLastActualExecution() {
        when(configService.get()).thenReturn(config(true, "0 0 8 * * MON"));
        var lastRun = ZonedDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant();
        var context = new SimpleTriggerContext(lastRun, lastRun, lastRun.plusSeconds(1));

        var next = task.getTrigger().nextExecution(context);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 3, 9, 8, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant());
    }

    @Test
    void run_enabled_buildsReportOnLookbackWindowAndSendsIt() {
        when(configService.get()).thenReturn(config(true, "0 0 8 * * MON"));
        var report = new FeedbackReport(LocalDateTime.now(), LocalDateTime.now(), 0, 0, 0, 0, List.of(), List.of());
        when(reportService.buildReport(any(), any())).thenReturn(report);

        task.getRunnable().run();

        var start = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        var end = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reportService).buildReport(start.capture(), end.capture());
        assertThat(Duration.between(start.getValue(), end.getValue())).isEqualTo(Duration.ofDays(7));
        verify(notificationService).sendWeeklyReport(eq(report), eq(List.of("a@zas.ch")));
    }

    @Test
    void run_disabled_doesNothing() {
        when(configService.get()).thenReturn(config(false, "0 0 8 * * MON"));

        task.getRunnable().run();

        verifyNoInteractions(reportService, notificationService);
    }

    private static FeedbackReportConfig config(boolean enabled, String cron) {
        return new FeedbackReportConfig(enabled, cron, "Europe/Zurich", List.of("a@zas.ch"), 7);
    }
}
