package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SourceFeedbackRepository extends JpaRepository<SourceFeedbackEntity, Long> {

    Optional<SourceFeedbackEntity> findByUserIdAndConversationIdAndMessageIdAndDocumentId(String userId, String conversationId, String messageId, String documentId);

    List<SourceFeedbackEntity> findByUserIdAndConversationIdAndMessageId(String userId, String conversationId, String messageId);

    List<SourceFeedbackEntity> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    @Query(value = """
        select sf.document_id as documentId,
            sum(case when sf.feedback_type = 'NEGATIVE' then 1 else 0 end) as negatives,
            sum(case when sf.feedback_type = 'POSITIVE' then 1 else 0 end) as positives
        from source_feedback sf
        where sf.timestamp >= :start and sf.timestamp < :end
        group by sf.document_id
        order by negatives desc, positives desc
        """, nativeQuery = true)
    List<DocAggRow> aggregateByDocument(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        Pageable page);


    interface DocAggRow { String getDocumentId(); long getNegatives(); long getPositives(); }
}
