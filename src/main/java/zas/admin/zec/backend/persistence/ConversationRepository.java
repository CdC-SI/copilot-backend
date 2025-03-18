package zas.admin.zec.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findByConversationIdAndUserId(String conversationId, String userId);
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
