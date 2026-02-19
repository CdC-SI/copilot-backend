package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, Long> {
    Optional<AttachmentEntity> findByIdAndConversationIdAndUserId(Long attachmentId, String conversationId, String userId);
    List<AttachmentEntity> findAllByConversationIdAndUserId(String conversationId, String userId);
    List<AttachmentEntity> findByConversationIdAndUserId(String conversationId, String userId);
    void deleteByIdAndUserId(Long attachmentId, String userId);
    void deleteByUserIdAndConversationId(String userId, String conversationId);
}
