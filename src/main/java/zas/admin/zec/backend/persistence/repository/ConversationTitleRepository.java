package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;

import java.util.List;
import java.util.Optional;

public interface ConversationTitleRepository extends JpaRepository<ConversationTitleEntity, Long> {
    List<ConversationTitleEntity> findByUserIdOrderByTimestamp(String userUuid);
    Optional<ConversationTitleEntity> findByUserIdAndConversationId(String userUuid, String conversationId);
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
