package zas.admin.zec.backend.actions.analyze;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import zas.admin.zec.backend.actions.converse.ConversationService;
import zas.admin.zec.backend.actions.converse.Source;
import zas.admin.zec.backend.actions.converse.SourceType;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;
import zas.admin.zec.backend.persistence.entity.FeedbackStatus;
import zas.admin.zec.backend.persistence.entity.MessageFeedbackEntity;
import zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity;
import zas.admin.zec.backend.persistence.repository.MessageFeedbackRepository;
import zas.admin.zec.backend.persistence.repository.SourceFeedbackRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackQueryServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 1, 10, 0);

    private final MessageFeedbackRepository msgRepo = mock(MessageFeedbackRepository.class);
    private final SourceFeedbackRepository srcRepo = mock(SourceFeedbackRepository.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final DocumentCatalogService docCatalog = mock(DocumentCatalogService.class);
    private final FeedbackQueryService service = new FeedbackQueryService(msgRepo, srcRepo, conversationService, docCatalog);

    @Test
    void window_mapsKnownRangesAndFallsBackToEpochForAll() {
        assertThat(days(service.window(null))).isEqualTo(30);
        assertThat(days(service.window(" "))).isEqualTo(30);
        assertThat(days(service.window("30d"))).isEqualTo(30);
        assertThat(days(service.window("7d"))).isEqualTo(7);
        assertThat(days(service.window("90d"))).isEqualTo(90);
        assertThat(service.window("all").start()).isEqualTo(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    @Test
    void listMessages_filtersByStatusAndCategoryAndSortsNewestFirst() {
        var older = message(1L, 1, FeedbackStatus.NEW, FeedbackCategory.WRONG_ANSWER, T0);
        var newer = message(2L, -1, FeedbackStatus.NEW, FeedbackCategory.WRONG_ANSWER, T0.plusHours(1));
        var treated = message(3L, -1, FeedbackStatus.TREATED, FeedbackCategory.WRONG_ANSWER, T0);
        var otherCategory = message(4L, -1, FeedbackStatus.NEW, FeedbackCategory.WRONG_SOURCE, T0);
        when(msgRepo.findByTimestampBetween(any(), any())).thenReturn(List.of(older, newer, treated, otherCategory));

        var result = service.listMessages("7d", false, FeedbackStatus.NEW, FeedbackCategory.WRONG_ANSWER);

        assertThat(result).extracting(FeedbackDTO.MessageFeedback::id).containsExactly(2L, 1L);
        assertThat(result.getFirst().sources()).isEmpty();
        assertThat(result.getFirst().score()).isEqualTo(-1);
        assertThat(result.getFirst().question()).isEqualTo("question-2");
        verify(conversationService, never()).getSourcesByMessageUuid(any(), any());
    }

    @Test
    void listMessages_withDetails_enrichesWithSourcesOfTheMessage() {
        var feedback = message(1L, 1, FeedbackStatus.NEW, null, T0);
        var source = new Source(SourceType.URL, "https://example.org");
        when(msgRepo.findByTimestampBetween(any(), any())).thenReturn(List.of(feedback));
        when(conversationService.getSourcesByMessageUuid("conv-1", "msg-1")).thenReturn(List.of(source));

        var result = service.listMessages(null, true, null, null);

        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.sources()).containsExactly(source);
            assertThat(m.conversationUuid()).isEqualTo("conv-1");
            assertThat(m.status()).isEqualTo(FeedbackStatus.NEW);
        });
    }

    @Test
    void listSources_filtersAndEnrichesWithCatalogTitleWhenKnown() {
        var known = source(1L, "doc-known", FeedbackStatus.NEW, FeedbackCategory.WRONG_SOURCE);
        var unknown = source(2L, "doc-unknown", FeedbackStatus.NEW, FeedbackCategory.WRONG_SOURCE);
        var filteredOut = source(3L, "doc-known", FeedbackStatus.OBSOLETE, FeedbackCategory.WRONG_SOURCE);
        when(srcRepo.findByTimestampBetween(any(), any())).thenReturn(List.of(known, unknown, filteredOut));
        when(docCatalog.findById("doc-known")).thenReturn(Optional.of(new DocumentCatalogService.Doc("Guide", "https://guide")));
        when(docCatalog.findById("doc-unknown")).thenReturn(Optional.empty());

        var result = service.listSources("90d", FeedbackStatus.NEW, FeedbackCategory.WRONG_SOURCE);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).documentTitle()).isEqualTo("Guide");
        assertThat(result.get(0).documentUrl()).isEqualTo("https://guide");
        assertThat(result.get(0).feedbackType()).isEqualTo("NEGATIVE");
        assertThat(result.get(1).documentTitle()).isNull();
        assertThat(result.get(1).documentUrl()).isNull();
    }

    @Test
    void stats_aggregatesCountsRatePerDayDocumentsStatusesAndCategories() {
        when(msgRepo.countByTimestampBetween(any(), any())).thenReturn(4L);
        when(msgRepo.countByTimestampBetweenAndScore(any(), any(), eq(1))).thenReturn(3L);
        when(msgRepo.countByTimestampBetweenAndScore(any(), any(), eq(-1))).thenReturn(1L);
        when(msgRepo.aggregatePerDay(any(), any())).thenReturn(List.of(dailyRow(LocalDate.of(2026, 3, 1), 3, 1)));
        when(srcRepo.aggregateByDocument(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(docRow("doc-a", 2, 1), docRow("doc-b", 0, 5)));
        when(docCatalog.findById("doc-a")).thenReturn(Optional.of(new DocumentCatalogService.Doc("Titre A", "url")));
        when(docCatalog.findById("doc-b")).thenReturn(Optional.empty());
        when(msgRepo.countByTimestampBetweenAndStatus(any(), any(), eq(FeedbackStatus.NEW))).thenReturn(2L);
        when(srcRepo.countByTimestampBetweenAndStatus(any(), any(), eq(FeedbackStatus.NEW))).thenReturn(1L);
        when(msgRepo.countByTimestampBetweenAndCategory(any(), any(), eq(FeedbackCategory.WRONG_ANSWER))).thenReturn(1L);
        when(srcRepo.countByTimestampBetweenAndCategory(any(), any(), eq(FeedbackCategory.WRONG_SOURCE))).thenReturn(2L);

        var stats = service.stats("30d");

        assertThat(stats.total()).isEqualTo(4);
        assertThat(stats.positive()).isEqualTo(3);
        assertThat(stats.negative()).isEqualTo(1);
        assertThat(stats.positiveRate()).isEqualTo(0.75);
        assertThat(stats.perDay()).containsExactly(new FeedbackDTO.PerDay("2026-03-01", 3, 1));
        assertThat(stats.byDocument()).containsExactly(
                new FeedbackDTO.ByDocument("doc-a", "Titre A", 2, 1),
                new FeedbackDTO.ByDocument("doc-b", "doc-b", 0, 5));
        assertThat(stats.byStatus()).contains(
                new FeedbackDTO.ByStatus(FeedbackStatus.NEW, 3),
                new FeedbackDTO.ByStatus(FeedbackStatus.TREATED, 0));
        assertThat(stats.byCategory()).contains(
                new FeedbackDTO.ByCategory(FeedbackCategory.WRONG_ANSWER, 1),
                new FeedbackDTO.ByCategory(FeedbackCategory.WRONG_SOURCE, 2),
                new FeedbackDTO.ByCategory(FeedbackCategory.INCOMPLETE_ANSWER, 0));
    }

    @Test
    void stats_withoutFeedback_hasZeroRate() {
        when(msgRepo.countByTimestampBetween(any(), any())).thenReturn(0L);

        var stats = service.stats(null);

        assertThat(stats.total()).isZero();
        assertThat(stats.positiveRate()).isZero();
        assertThat(stats.perDay()).isEmpty();
        assertThat(stats.byDocument()).isEmpty();
    }

    @Test
    void updateMessageFeedbackStatus_savesNewStatus() {
        var entity = message(5L, -1, FeedbackStatus.NEW, null, T0);
        when(msgRepo.findById(5L)).thenReturn(Optional.of(entity));

        var result = service.updateMessageFeedbackStatus(5L, FeedbackStatus.TREATED);

        assertThat(result.status()).isEqualTo(FeedbackStatus.TREATED);
        assertThat(entity.getStatus()).isEqualTo(FeedbackStatus.TREATED);
        verify(msgRepo).save(entity);
    }

    @Test
    void updateMessageFeedbackStatus_unknownId_throws() {
        when(msgRepo.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMessageFeedbackStatus(9L, FeedbackStatus.TREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9");
        verify(msgRepo, never()).save(any());
    }

    @Test
    void updateSourceFeedbackStatus_savesNewStatus() {
        var entity = source(6L, "doc", FeedbackStatus.NEW, null);
        when(srcRepo.findById(6L)).thenReturn(Optional.of(entity));
        when(docCatalog.findById("doc")).thenReturn(Optional.empty());

        var result = service.updateSourceFeedbackStatus(6L, FeedbackStatus.OBSOLETE);

        assertThat(result.status()).isEqualTo(FeedbackStatus.OBSOLETE);
        verify(srcRepo).save(entity);
    }

    @Test
    void updateSourceFeedbackStatus_unknownId_throws() {
        when(srcRepo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSourceFeedbackStatus(7L, FeedbackStatus.TREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static long days(FeedbackQueryService.TimeWindow window) {
        return Duration.between(window.start(), window.end()).toDays();
    }

    static MessageFeedbackEntity message(Long id, int score, FeedbackStatus status, FeedbackCategory category, LocalDateTime timestamp) {
        var entity = new MessageFeedbackEntity();
        entity.setId(id);
        entity.setUserUuid("user-" + id);
        entity.setConversationUuid("conv-" + id);
        entity.setMessageUuid("msg-" + id);
        entity.setScore(score);
        entity.setComment("comment-" + id);
        entity.setTimestamp(timestamp);
        entity.setQuestion("question-" + id);
        entity.setAnswer("answer-" + id);
        entity.setStatus(status);
        entity.setCategory(category);
        return entity;
    }

    static SourceFeedbackEntity source(Long id, String documentId, FeedbackStatus status, FeedbackCategory category) {
        var entity = new SourceFeedbackEntity();
        entity.setId(id);
        entity.setUserUuid("user-" + id);
        entity.setConversationUuid("conv-" + id);
        entity.setMessageUuid("msg-" + id);
        entity.setDocumentId(documentId);
        entity.setFeedbackType(SourceFeedbackEntity.FeedbackType.NEGATIVE);
        entity.setTimestamp(T0);
        entity.setQuestion("question-" + id);
        entity.setStatus(status);
        entity.setCategory(category);
        return entity;
    }

    private static MessageFeedbackRepository.DailyCountRow dailyRow(LocalDate day, long positive, long negative) {
        return new MessageFeedbackRepository.DailyCountRow() {
            @Override public LocalDate getDay() { return day; }
            @Override public long getPositive() { return positive; }
            @Override public long getNegative() { return negative; }
        };
    }

    private static SourceFeedbackRepository.DocAggRow docRow(String documentId, long negatives, long positives) {
        return new SourceFeedbackRepository.DocAggRow() {
            @Override public String getDocumentId() { return documentId; }
            @Override public long getNegatives() { return negatives; }
            @Override public long getPositives() { return positives; }
        };
    }
}
