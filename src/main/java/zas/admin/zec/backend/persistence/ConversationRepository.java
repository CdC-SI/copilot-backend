package zas.admin.zec.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<MessageEntity, Long> {
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
