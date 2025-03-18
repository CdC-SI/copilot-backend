package zas.admin.zec.backend.actions.rate;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.MessageFeedbackEntity;
import zas.admin.zec.backend.persistence.MessageFeedbackRepository;

import java.time.LocalDateTime;

@Service
public class FeedbackService {
    private final MessageFeedbackRepository messageFeedbackRepository;

    public FeedbackService(MessageFeedbackRepository messageFeedbackRepository) {
        this.messageFeedbackRepository = messageFeedbackRepository;
    }

    public void sendFeedback(String userId, Feedback feedback) {
        messageFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuid(userId, feedback.conversationId(), feedback.messageId())
                .ifPresentOrElse(
                        messageFeedback -> updateFeedback(messageFeedback, feedback),
                        () -> createFeedback(userId, feedback));

    }

    private void updateFeedback(MessageFeedbackEntity toUpdate, Feedback feedback) {
        toUpdate.setScore(feedback.isPositive() ? 1 : -1);
        toUpdate.setComment(feedback.comment());
        toUpdate.setTimestamp(LocalDateTime.now());

        messageFeedbackRepository.save(toUpdate);
    }

    private void createFeedback(String userId, Feedback feedback) {
        var messageFeedback = new MessageFeedbackEntity();
        messageFeedback.setUserUuid(userId);
        messageFeedback.setConversationUuid(feedback.conversationId());
        messageFeedback.setMessageUuid(feedback.messageId());
        messageFeedback.setScore(feedback.isPositive() ? 1 : -1);
        messageFeedback.setComment(feedback.comment());
        messageFeedback.setTimestamp(LocalDateTime.now());

        messageFeedbackRepository.save(messageFeedback);
    }
}
