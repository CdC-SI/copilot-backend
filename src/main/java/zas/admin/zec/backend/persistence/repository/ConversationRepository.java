package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.MessageEntity;

import java.util.List;

public interface ConversationRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findByConversationIdAndUserIdOrderByTimestamp(String conversationId, String userId, Limit limit);
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
