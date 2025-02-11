package zas.admin.zec.backend.conversations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationTitleRepository extends JpaRepository<ConversationTitleEntity, Long> {
    Optional<ConversationTitleEntity> findByUserIdAndConversationId(String userUuid, String conversationId);
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
