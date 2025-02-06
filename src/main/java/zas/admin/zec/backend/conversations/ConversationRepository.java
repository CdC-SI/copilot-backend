package zas.admin.zec.backend.conversations;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<MessageEntity, Long> {
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
