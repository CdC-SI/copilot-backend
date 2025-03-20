package zas.admin.zec.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedbackEntity, Integer> {
    Optional<MessageFeedbackEntity> findByUserUuidAndConversationUuidAndMessageUuid(String userUuid, String conversationUuid, String messageUuid);
}
