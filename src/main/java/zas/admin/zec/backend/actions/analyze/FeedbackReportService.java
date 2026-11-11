package zas.admin.zec.backend.actions.analyze;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;
import zas.admin.zec.backend.persistence.entity.FeedbackStatus;
import zas.admin.zec.backend.persistence.repository.MessageFeedbackRepository;
import zas.admin.zec.backend.persistence.repository.SourceFeedbackRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Construit le rapport hebdomadaire des feedbacks (message + source) sur une fenêtre temporelle
 * donnée : volumétrie totale, répartition par statut/catégorie et liste des feedbacks encore
 * en attente de traitement (statut {@code NEW}).
 */
@Service
public class FeedbackReportService {

    private final MessageFeedbackRepository msgRepo;
    private final SourceFeedbackRepository srcRepo;

    public FeedbackReportService(MessageFeedbackRepository msgRepo, SourceFeedbackRepository srcRepo) {
        this.msgRepo = msgRepo;
        this.srcRepo = srcRepo;
    }

    public record PendingItem(Instant timestamp, String kind, FeedbackCategory category, String question) {}

    public record FeedbackReport(
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            long total,
            long treated,
            long obsolete,
            long stillNew,
            List<FeedbackDTO.ByCategory> byCategory,
            List<PendingItem> pendingItems) {}

    public FeedbackReport buildReport(LocalDateTime start, LocalDateTime end) {
        long msgTotal = msgRepo.countByTimestampBetween(start, end);
        long srcTotal = srcRepo.countByTimestampBetween(start, end);
        long total = msgTotal + srcTotal;

        long treated = msgRepo.countByTimestampBetweenAndStatus(start, end, FeedbackStatus.TREATED)
                + srcRepo.countByTimestampBetweenAndStatus(start, end, FeedbackStatus.TREATED);
        long obsolete = msgRepo.countByTimestampBetweenAndStatus(start, end, FeedbackStatus.OBSOLETE)
                + srcRepo.countByTimestampBetweenAndStatus(start, end, FeedbackStatus.OBSOLETE);
        long stillNew = msgRepo.countByTimestampBetweenAndStatus(start, end, FeedbackStatus.NEW)
                + srcRepo.countByTimestampBetweenAndStatus(start, end, FeedbackStatus.NEW);

        var byCategory = Arrays.stream(FeedbackCategory.values())
                .map(c -> new FeedbackDTO.ByCategory(c,
                        msgRepo.countByTimestampBetweenAndCategory(start, end, c)
                                + srcRepo.countByTimestampBetweenAndCategory(start, end, c)))
                .toList();

        var pendingMessages = msgRepo.findByTimestampBetweenAndStatus(start, end, FeedbackStatus.NEW).stream()
                .map(m -> new PendingItem(
                        m.getTimestamp().atZone(ZoneId.systemDefault()).toInstant(), "Message", m.getCategory(), m.getQuestion()));

        var pendingSources = srcRepo.findByTimestampBetweenAndStatus(start, end, FeedbackStatus.NEW).stream()
                .map(s -> new PendingItem(
                        s.getTimestamp().atZone(ZoneId.systemDefault()).toInstant(), "Source", s.getCategory(), s.getQuestion()));

        var pendingItems = java.util.stream.Stream.concat(pendingMessages, pendingSources)
                .sorted(Comparator.comparing(PendingItem::timestamp))
                .toList();

        return new FeedbackReport(start, end, total, treated, obsolete, stillNew, byCategory, pendingItems);
    }
}
