package zas.admin.zec.backend.actions.analyze;

import org.junit.jupiter.api.Test;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;
import zas.admin.zec.backend.persistence.entity.FeedbackStatus;
import zas.admin.zec.backend.persistence.repository.MessageFeedbackRepository;
import zas.admin.zec.backend.persistence.repository.SourceFeedbackRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static zas.admin.zec.backend.actions.analyze.FeedbackQueryServiceTest.message;
import static zas.admin.zec.backend.actions.analyze.FeedbackQueryServiceTest.source;

class FeedbackReportServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 1, 0, 0);
    private static final LocalDateTime END = START.plusDays(7);

    private final MessageFeedbackRepository msgRepo = mock(MessageFeedbackRepository.class);
    private final SourceFeedbackRepository srcRepo = mock(SourceFeedbackRepository.class);
    private final FeedbackReportService service = new FeedbackReportService(msgRepo, srcRepo);

    @Test
    void buildReport_sumsMessageAndSourceFeedbacksAndListsPendingOnesChronologically() {
        when(msgRepo.countByTimestampBetween(START, END)).thenReturn(5L);
        when(srcRepo.countByTimestampBetween(START, END)).thenReturn(3L);
        when(msgRepo.countByTimestampBetweenAndStatus(START, END, FeedbackStatus.TREATED)).thenReturn(2L);
        when(srcRepo.countByTimestampBetweenAndStatus(START, END, FeedbackStatus.TREATED)).thenReturn(1L);
        when(msgRepo.countByTimestampBetweenAndStatus(START, END, FeedbackStatus.OBSOLETE)).thenReturn(1L);
        when(msgRepo.countByTimestampBetweenAndStatus(START, END, FeedbackStatus.NEW)).thenReturn(2L);
        when(srcRepo.countByTimestampBetweenAndStatus(START, END, FeedbackStatus.NEW)).thenReturn(2L);
        when(msgRepo.countByTimestampBetweenAndCategory(eq(START), eq(END), eq(FeedbackCategory.WRONG_ANSWER))).thenReturn(4L);
        when(srcRepo.countByTimestampBetweenAndCategory(eq(START), eq(END), eq(FeedbackCategory.WRONG_SOURCE))).thenReturn(2L);

        var lateMessage = message(1L, -1, FeedbackStatus.NEW, FeedbackCategory.WRONG_ANSWER, START.plusDays(3));
        var earlyMessage = message(2L, -1, FeedbackStatus.NEW, null, START.plusDays(1));
        var sourceFeedback = source(3L, "doc", FeedbackStatus.NEW, FeedbackCategory.WRONG_SOURCE);
        sourceFeedback.setTimestamp(START.plusDays(2));
        when(msgRepo.findByTimestampBetweenAndStatus(START, END, FeedbackStatus.NEW)).thenReturn(List.of(lateMessage, earlyMessage));
        when(srcRepo.findByTimestampBetweenAndStatus(START, END, FeedbackStatus.NEW)).thenReturn(List.of(sourceFeedback));

        var report = service.buildReport(START, END);

        assertThat(report.periodStart()).isEqualTo(START);
        assertThat(report.periodEnd()).isEqualTo(END);
        assertThat(report.total()).isEqualTo(8);
        assertThat(report.treated()).isEqualTo(3);
        assertThat(report.obsolete()).isEqualTo(1);
        assertThat(report.stillNew()).isEqualTo(4);
        assertThat(report.byCategory()).containsExactly(
                new FeedbackDTO.ByCategory(FeedbackCategory.WRONG_SOURCE, 2),
                new FeedbackDTO.ByCategory(FeedbackCategory.WRONG_ANSWER, 4),
                new FeedbackDTO.ByCategory(FeedbackCategory.INCOMPLETE_ANSWER, 0));
        assertThat(report.pendingItems())
                .extracting(FeedbackReportService.PendingItem::kind, FeedbackReportService.PendingItem::question)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Message", "question-2"),
                        org.assertj.core.groups.Tuple.tuple("Source", "question-3"),
                        org.assertj.core.groups.Tuple.tuple("Message", "question-1"));
    }

    @Test
    void buildReport_withoutFeedback_isEmpty() {
        var report = service.buildReport(START, END);

        assertThat(report.total()).isZero();
        assertThat(report.pendingItems()).isEmpty();
        assertThat(report.byCategory()).allSatisfy(c -> assertThat(c.count()).isZero());
    }
}
