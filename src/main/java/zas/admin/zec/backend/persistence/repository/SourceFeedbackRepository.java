package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity;

import java.util.List;
import java.util.Optional;

public interface SourceFeedbackRepository extends JpaRepository<SourceFeedbackEntity, Long> {

    Optional<SourceFeedbackEntity> findByUserIdAndConversationIdAndMessageIdAndDocumentId(String userId, String conversationId, String messageId, String documentId);
    List<SourceFeedbackEntity> findByUserIdAndConversationIdAndMessageId(String userId, String conversationId, String messageId);
}
