package zas.admin.zec.backend.actions.analyze;

import zas.admin.zec.backend.actions.converse.Source;

import java.time.Instant;
import java.util.List;

public class FeedbackDTO {
    // --- Message feedback ---
    public record MessageFeedback(
            Integer id,
            String userUuid,
            String conversationUuid,
            String messageUuid,
            int score,
            String comment,
            Instant timestamp,

            // optional enriched fields
            String question,
            String answer,
            List<Source> sources
    ) {}

    // --- Source feedback row ---
    public record SourceFeedback(
            Long id,
            String userUuid,
            String conversationUuid,
            String messageUuid,
            String documentId,
            String feedbackType, // POSITIVE | NEGATIVE
            String comment,
            Instant timestamp,

            // optional enrichment
            String documentTitle,
            String documentUrl,
            String question,
            String answer
    ) {}

    // --- Stats ---
    public record Stats(
            long total,
            long positive,
            long negative,
            double positiveRate,
            List<PerDay> perDay,
            List<ByDocument> byDocument
    ) {}

    public record PerDay(String date, long positive, long negative) {}
    public record ByDocument(String documentId, String title, long negatives, long positives) {}
}
