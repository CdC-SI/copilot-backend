package zas.admin.zec.backend.actions.rate;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.entity.AbstractFeedbackEntity;
import zas.admin.zec.backend.persistence.entity.FeedbackStatus;
import zas.admin.zec.backend.persistence.entity.MessageFeedbackEntity;
import zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity;
import zas.admin.zec.backend.persistence.repository.MessageFeedbackRepository;
import zas.admin.zec.backend.persistence.repository.SourceFeedbackRepository;

import java.time.LocalDateTime;
import java.util.List;

import static zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity.FeedbackType.NEGATIVE;
import static zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity.FeedbackType.POSITIVE;

@Service
public class FeedbackService {
    private final MessageFeedbackRepository messageFeedbackRepository;
    private final SourceFeedbackRepository sourceFeedbackRepository;
    private final FeedbackNotificationService notificationService;

    public FeedbackService(MessageFeedbackRepository messageFeedbackRepository,
                            SourceFeedbackRepository sourceFeedbackRepository,
                            FeedbackNotificationService notificationService) {
        this.messageFeedbackRepository = messageFeedbackRepository;
        this.sourceFeedbackRepository = sourceFeedbackRepository;
        this.notificationService = notificationService;
    }

    public void sendFeedback(String userId, Feedback feedback) {
        messageFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuid(userId, feedback.conversationId(), feedback.messageId())
                .ifPresentOrElse(
                        messageFeedback -> updateFeedback(messageFeedback, feedback),
                        () -> createFeedback(userId, feedback));

    }

    public void sendFeedback(String userId, SourceFeedback feedback) {
        sourceFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuidAndDocumentId(
                        userId, feedback.conversationId(), feedback.messageId(), feedback.documentId())
                .ifPresentOrElse(
                        entity -> updateFeedback(entity, feedback),
                        () -> createFeedback(userId, feedback));
    }

    private void updateFeedback(MessageFeedbackEntity toUpdate, Feedback feedback) {
        toUpdate.setScore(feedback.isPositive() ? 1 : -1);
        toUpdate.setComment(feedback.comment());
        toUpdate.setTimestamp(LocalDateTime.now());
        toUpdate.setCategory(feedback.category());
        toUpdate.setStatus(FeedbackStatus.NEW);

        messageFeedbackRepository.save(toUpdate);
        notifyIfNegative(toUpdate);
    }

    private void updateFeedback(SourceFeedbackEntity toUpdate, SourceFeedback feedback) {
        toUpdate.setFeedbackType(feedback.isPositive() ? POSITIVE : NEGATIVE);
        toUpdate.setComment(feedback.comment());
        toUpdate.setTimestamp(LocalDateTime.now());
        toUpdate.setCategory(feedback.category());
        toUpdate.setStatus(FeedbackStatus.NEW);

        sourceFeedbackRepository.save(toUpdate);
        notifyIfNegative(toUpdate);
    }

    private void createFeedback(String userId, Feedback feedback) {
        var messageFeedback = new MessageFeedbackEntity();
        messageFeedback.setUserUuid(userId);
        messageFeedback.setConversationUuid(feedback.conversationId());
        messageFeedback.setMessageUuid(feedback.messageId());
        messageFeedback.setScore(feedback.isPositive() ? 1 : -1);
        messageFeedback.setComment(feedback.comment());
        messageFeedback.setTimestamp(LocalDateTime.now());
        messageFeedback.setQuestion(feedback.question());
        messageFeedback.setAnswer(feedback.answer());
        messageFeedback.setCategory(feedback.category());

        messageFeedbackRepository.save(messageFeedback);
        notifyIfNegative(messageFeedback);
    }

    private void createFeedback(String userId, SourceFeedback feedback) {
        var sourceFeedback = new SourceFeedbackEntity();
        sourceFeedback.setUserUuid(userId);
        sourceFeedback.setConversationUuid(feedback.conversationId());
        sourceFeedback.setMessageUuid(feedback.messageId());
        sourceFeedback.setDocumentId(feedback.documentId());
        sourceFeedback.setFeedbackType(feedback.isPositive() ? POSITIVE : NEGATIVE);
        sourceFeedback.setComment(feedback.comment());
        sourceFeedback.setTimestamp(LocalDateTime.now());
        sourceFeedback.setQuestion(feedback.question());
        sourceFeedback.setAnswer(feedback.answer());
        sourceFeedback.setCategory(feedback.category());

        sourceFeedbackRepository.save(sourceFeedback);
        notifyIfNegative(sourceFeedback);
    }

    private void notifyIfNegative(AbstractFeedbackEntity feedback) {
        if (feedback.isNegative()) {
            notificationService.notifyNegativeFeedback(feedback);
        }
    }

    public List<SourceFeedback> getFeedbacks(String userUuid, String conversationId, String messageId) {
        return sourceFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuid(userUuid, conversationId, messageId)
                .stream()
                .map(entity -> new SourceFeedback(
                        entity.getConversationUuid(),
                        entity.getMessageUuid(),
                        entity.getDocumentId(),
                        entity.getFeedbackType() == POSITIVE,
                        entity.getComment(),
                        entity.getQuestion(),
                        entity.getAnswer(),
                        entity.getCategory()))
                .toList();
    }
}

