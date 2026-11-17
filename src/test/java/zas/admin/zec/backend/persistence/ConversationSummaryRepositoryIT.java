package zas.admin.zec.backend.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import zas.admin.zec.backend.persistence.repository.ConversationSummaryRepository;
import zas.admin.zec.backend.persistence.repository.ConversationSummaryRepository.ConversationSummary;
import zas.admin.zec.backend.tools.document.AbstractDocumentLookupIT;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Résumé persisté d'une conversation (V24 : {@code conversation_summary}), un par conversation. */
class ConversationSummaryRepositoryIT extends AbstractDocumentLookupIT {

    @Autowired
    private ConversationSummaryRepository repository;

    @Test
    void summaryIsUpsertedPerConversationAndDeleted() {
        var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        var first = new ConversationSummary("user-it", "conv-sum", "m2", "- Résumé 1", "conversation-summary/fr@aaaaaaaaaaaa",
                120, 15, now);
        var second = new ConversationSummary("user-it", "conv-sum", "m4", "- Résumé 2", "conversation-summary/fr@bbbbbbbbbbbb",
                null, null, now.plusMinutes(1));
        var other = new ConversationSummary("other-user", "conv-sum", "x", "- Autre", "conversation-summary/de@cccccccccccc",
                1, 1, now);

        repository.save(first);
        repository.save(other);
        assertEquals(first, repository.find("user-it", "conv-sum").orElseThrow());

        repository.save(second);
        assertEquals(second, repository.find("user-it", "conv-sum").orElseThrow());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM conversation_summary WHERE user_uuid = 'user-it'", Integer.class));

        repository.delete("user-it", "conv-sum");
        assertTrue(repository.find("user-it", "conv-sum").isEmpty());
        assertEquals(other, repository.find("other-user", "conv-sum").orElseThrow());
        repository.delete("other-user", "conv-sum");
    }
}
