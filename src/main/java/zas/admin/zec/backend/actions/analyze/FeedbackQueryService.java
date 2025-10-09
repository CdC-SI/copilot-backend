package zas.admin.zec.backend.actions.analyze;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO.*;
import zas.admin.zec.backend.actions.converse.ConversationService;
import zas.admin.zec.backend.actions.converse.Source;
import zas.admin.zec.backend.persistence.entity.MessageFeedbackEntity;
import zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity;
import zas.admin.zec.backend.persistence.repository.MessageFeedbackRepository;
import zas.admin.zec.backend.persistence.repository.SourceFeedbackRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class FeedbackQueryService {

    private final MessageFeedbackRepository msgRepo;
    private final SourceFeedbackRepository srcRepo;
    private final ConversationService conversationService;
    private final DocumentCatalogService docCatalog;

    public FeedbackQueryService(MessageFeedbackRepository msgRepo,
                                SourceFeedbackRepository srcRepo,
                                ConversationService conversationService,
                                DocumentCatalogService docCatalog) {

        this.msgRepo = msgRepo;
        this.srcRepo = srcRepo;
        this.conversationService = conversationService;
        this.docCatalog = docCatalog;
    }

    public record TimeWindow(LocalDateTime start, LocalDateTime end) {}
    public TimeWindow window(String range) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start;
        if (range == null || range.isBlank() || "30d".equals(range)) start = end.minusDays(30);
        else if ("7d".equals(range)) start = end.minusDays(7);
        else if ("90d".equals(range)) start = end.minusDays(90);
        else start = LocalDateTime.of(1970, 1, 1, 0, 0);
        return new TimeWindow(start, end);
    }

    public List<MessageFeedback> listMessages(String range, boolean includeDetails) {
        TimeWindow w = window(range);
        return msgRepo.findByTimestampBetween(w.start(), w.end())
                .stream()
                .sorted(Comparator.comparing(MessageFeedbackEntity::getTimestamp).reversed())
                .map(m -> toMessageDTO(m, includeDetails))
                .toList();
    }

    public List<FeedbackDTO.SourceFeedback> listSources(String range) {
        TimeWindow w = window(range);
        return srcRepo.findByTimestampBetween(w.start(), w.end()).stream()
                .map(this::toSourceDTO)
                .toList();
    }

    public Stats stats(String range) {
        TimeWindow w = window(range);
        long total = msgRepo.countByTimestampBetween(w.start(), w.end());
        long positive = msgRepo.countByTimestampBetweenAndScore(w.start(), w.end(), 1);
        long negative = msgRepo.countByTimestampBetweenAndScore(w.start(), w.end(), -1);
        double rate = total == 0 ? 0d : (double) positive / (double) total;

        var perDay = msgRepo.aggregatePerDay(w.start(), w.end()).stream()
                .map(r -> new PerDay(r.getDay().toLocalDate().toString(), r.getPositive(), r.getNegative()))
                .toList();

        var topDocs = srcRepo.aggregateByDocument(w.start(), w.end(), PageRequest.of(0, 50)).stream()
                .map(r -> {
                    String title = docCatalog.findById(r.getDocumentId())
                            .map(DocumentCatalogService.Doc::title)
                            .orElse(r.getDocumentId());
                    return new ByDocument(r.getDocumentId(), title, r.getNegatives(), r.getPositives());
                })
                .toList();

        return new Stats(total, positive, negative, rate, perDay, topDocs);
    }

    private MessageFeedback toMessageDTO(MessageFeedbackEntity e, boolean includeDetails) {
        List<Source> sources = List.of();
        if (includeDetails) {
            sources = conversationService.getSourcesByMessageUuid(e.getConversationUuid(), e.getMessageUuid());
        }

        return new MessageFeedback(
                e.getId(), e.getUserUuid(), e.getConversationUuid(), e.getMessageUuid(),
                e.getScore(), e.getComment(), e.getTimestamp().atZone(ZoneId.systemDefault()).toInstant(),
                e.getQuestion(), e.getAnswer(), sources
        );
    }

    private SourceFeedback toSourceDTO(SourceFeedbackEntity s) {
        String title = null;
        String url = null;

        var doc = docCatalog.findById(s.getDocumentId());
        if (doc.isPresent()) {
            title = doc.get().title();
            url = doc.get().url();
        }

        return new SourceFeedback(
                s.getId(), s.getUserId(), s.getConversationId(), s.getMessageId(), s.getDocumentId(),
                s.getFeedbackType().name(), s.getComment(), s.getTimestamp().atZone(ZoneId.systemDefault()).toInstant(),
                title, url, s.getQuestion(), s.getAnswer());
    }
}
