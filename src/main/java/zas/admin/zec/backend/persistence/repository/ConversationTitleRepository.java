package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationTitleRepository extends JpaRepository<ConversationTitleEntity, Long> {
    List<ConversationTitleEntity> findByUserIdOrderByTimestamp(String userUuid);
    Optional<ConversationTitleEntity> findByUserIdAndConversationId(String userUuid, String conversationId);
    void deleteByUserIdAndConversationId(String userId, String conversationId);

    /**
     * Crée le titre de la conversation s'il n'existe pas encore (atomique grâce à la contrainte
     * unique {@code (user_uuid, conversation_uuid)}).
     *
     * @return 1 si la ligne a été créée par cet appel, 0 si la conversation avait déjà un titre
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO chat_title (user_uuid, conversation_uuid, chat_title, timestamp, conversation_type)
            VALUES (:userId, :conversationId, :title, :timestamp, :conversationType)
            ON CONFLICT (user_uuid, conversation_uuid) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId,
                       @Param("conversationId") String conversationId,
                       @Param("title") String title,
                       @Param("timestamp") LocalDateTime timestamp,
                       @Param("conversationType") String conversationType);
}
