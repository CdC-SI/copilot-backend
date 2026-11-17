package zas.admin.zec.backend.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/** Résumés persistés des conversations ({@code conversation_summary}, un par conversation). */
@Repository
public class ConversationSummaryRepository {

    /**
     * Résumé des messages d'une conversation jusqu'à {@code coveredMessageId} inclus.
     *
     * @param promptVersion version du prompt de résumé ({@code <clé>/<langue>@<version>})
     */
    public record ConversationSummary(String userId, String conversationId, String coveredMessageId, String summary,
                                      String promptVersion, Integer promptTokens, Integer completionTokens,
                                      LocalDateTime updatedAt) {}

    private final JdbcTemplate jdbcTemplate;

    public ConversationSummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ConversationSummary> find(String userId, String conversationId) {
        return jdbcTemplate.query("""
                        SELECT covered_message_uuid, summary, prompt_version, prompt_tokens, completion_tokens, updated_at
                        FROM conversation_summary WHERE user_uuid = ? AND conversation_uuid = ?""",
                (rs, i) -> new ConversationSummary(userId, conversationId, rs.getString(1), rs.getString(2),
                        rs.getString(3), (Integer) rs.getObject(4), (Integer) rs.getObject(5),
                        rs.getTimestamp(6).toLocalDateTime()),
                userId, conversationId).stream().findFirst();
    }

    public void save(ConversationSummary summary) {
        jdbcTemplate.update("""
                        INSERT INTO conversation_summary (user_uuid, conversation_uuid, covered_message_uuid, summary,
                                                          prompt_version, prompt_tokens, completion_tokens, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (user_uuid, conversation_uuid) DO UPDATE SET
                            covered_message_uuid = EXCLUDED.covered_message_uuid, summary = EXCLUDED.summary,
                            prompt_version = EXCLUDED.prompt_version, prompt_tokens = EXCLUDED.prompt_tokens,
                            completion_tokens = EXCLUDED.completion_tokens, updated_at = EXCLUDED.updated_at""",
                summary.userId(), summary.conversationId(), summary.coveredMessageId(), summary.summary(),
                summary.promptVersion(), summary.promptTokens(), summary.completionTokens(),
                Timestamp.valueOf(summary.updatedAt()));
    }

    public void delete(String userId, String conversationId) {
        jdbcTemplate.update("DELETE FROM conversation_summary WHERE user_uuid = ? AND conversation_uuid = ?",
                userId, conversationId);
    }
}
