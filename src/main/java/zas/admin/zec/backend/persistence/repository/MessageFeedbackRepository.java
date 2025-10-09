package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zas.admin.zec.backend.persistence.entity.MessageFeedbackEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedbackEntity, Integer> {
    Optional<MessageFeedbackEntity> findByUserUuidAndConversationUuidAndMessageUuid(String userUuid, String conversationUuid, String messageUuid);

    List<MessageFeedbackEntity> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    long countByTimestampBetween(LocalDateTime start, LocalDateTime end);

    long countByTimestampBetweenAndScore(LocalDateTime start, LocalDateTime end, Integer score);

    @Query(value = """
        select cast(cf.timestamp as date) as day,
            sum(case when cf.score = 1 then 1 else 0 end) as positive,
            sum(case when cf.score = -1 then 1 else 0 end) as negative
        from chat_feedback cf
        where cf.timestamp >= :start and cf.timestamp < :end
        group by day
        order by day
        """, nativeQuery = true)
    List<DailyCountRow> aggregatePerDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    interface DailyCountRow { java.sql.Date getDay(); long getPositive(); long getNegative(); }
}
