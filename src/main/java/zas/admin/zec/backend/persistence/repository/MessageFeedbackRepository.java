package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.MessageFeedbackEntity;

import java.util.Optional;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedbackEntity, Integer> {
    Optional<MessageFeedbackEntity> findByUserUuidAndConversationUuidAndMessageUuid(String userUuid, String conversationUuid, String messageUuid);
}
