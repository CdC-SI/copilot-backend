package zas.admin.zec.backend.actions.rate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;
import zas.admin.zec.backend.persistence.entity.FeedbackStatus;
import zas.admin.zec.backend.persistence.entity.MessageFeedbackEntity;
import zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity;
import zas.admin.zec.backend.persistence.repository.MessageFeedbackRepository;
import zas.admin.zec.backend.persistence.repository.SourceFeedbackRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private MessageFeedbackRepository messageFeedbackRepository;
    @Mock
    private SourceFeedbackRepository sourceFeedbackRepository;
    @Mock
    private FeedbackNotificationService notificationService;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    @DisplayName("sendFeedback creates new message feedback when none exists")
    void sendFeedback_createsNew_whenNoExistingFeedback() {
        Feedback feedback = new Feedback("conv1", "msg1", true, "Great", "Q?", "A.", null);
        when(messageFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuid("user1", "conv1", "msg1"))
                .thenReturn(Optional.empty());

        feedbackService.sendFeedback("user1", feedback);

        ArgumentCaptor<MessageFeedbackEntity> captor = ArgumentCaptor.forClass(MessageFeedbackEntity.class);
        verify(messageFeedbackRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getScore());
        assertEquals("Great", captor.getValue().getComment());
        assertEquals("user1", captor.getValue().getUserUuid());
        assertEquals(FeedbackStatus.NEW, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("sendFeedback updates existing message feedback and notifies moderators when negative")
    void sendFeedback_updatesExisting() {
        Feedback feedback = new Feedback("conv1", "msg1", false, "Bad", null, null, FeedbackCategory.WRONG_ANSWER);
        MessageFeedbackEntity existing = new MessageFeedbackEntity();
        existing.setScore(1);
        when(messageFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuid("user1", "conv1", "msg1"))
                .thenReturn(Optional.of(existing));

        feedbackService.sendFeedback("user1", feedback);

        verify(messageFeedbackRepository).save(existing);
        assertEquals(-1, existing.getScore());
        assertEquals("Bad", existing.getComment());
        assertEquals(FeedbackCategory.WRONG_ANSWER, existing.getCategory());
        assertEquals(FeedbackStatus.NEW, existing.getStatus());
        verify(notificationService).notifyNegativeFeedback(existing);
    }

    @Test
    @DisplayName("sendFeedback for source creates new when none exists")
    void sendSourceFeedback_createsNew() {
        SourceFeedback feedback = new SourceFeedback("conv1", "msg1", "doc1", true, "Good source", "Q", "A", null);
        when(sourceFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuidAndDocumentId("user1", "conv1", "msg1", "doc1"))
                .thenReturn(Optional.empty());

        feedbackService.sendFeedback("user1", feedback);

        verify(sourceFeedbackRepository).save(any(SourceFeedbackEntity.class));
    }

    @Test
    @DisplayName("sendFeedback for source notifies moderators when negative")
    void sendSourceFeedback_negative_notifies() {
        SourceFeedback feedback = new SourceFeedback("conv1", "msg1", "doc1", false, "Wrong source", "Q", "A", FeedbackCategory.WRONG_SOURCE);
        when(sourceFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuidAndDocumentId("user1", "conv1", "msg1", "doc1"))
                .thenReturn(Optional.empty());

        feedbackService.sendFeedback("user1", feedback);

        ArgumentCaptor<SourceFeedbackEntity> captor = ArgumentCaptor.forClass(SourceFeedbackEntity.class);
        verify(sourceFeedbackRepository).save(captor.capture());
        verify(notificationService).notifyNegativeFeedback(captor.getValue());
    }

    @Test
    @DisplayName("getFeedbacks returns mapped list")
    void getFeedbacks_returnsMappedList() {
        SourceFeedbackEntity entity = new SourceFeedbackEntity();
        entity.setConversationUuid("conv1");
        entity.setMessageUuid("msg1");
        entity.setDocumentId("doc1");
        entity.setFeedbackType(SourceFeedbackEntity.FeedbackType.POSITIVE);
        entity.setComment("Nice");
        entity.setQuestion("Q");
        entity.setAnswer("A");

        when(sourceFeedbackRepository.findByUserUuidAndConversationUuidAndMessageUuid("user1", "conv1", "msg1"))
                .thenReturn(List.of(entity));

        List<SourceFeedback> result = feedbackService.getFeedbacks("user1", "conv1", "msg1");

        assertEquals(1, result.size());
        assertTrue(result.get(0).isPositive());
        assertEquals("doc1", result.get(0).documentId());
    }
}
