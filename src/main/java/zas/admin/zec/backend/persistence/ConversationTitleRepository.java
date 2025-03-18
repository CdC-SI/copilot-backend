package zas.admin.zec.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationTitleRepository extends JpaRepository<ConversationTitleEntity, Long> {
    List<ConversationTitleEntity> findByUserId(String userUuid);
    Optional<ConversationTitleEntity> findByUserIdAndConversationId(String userUuid, String conversationId);
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
