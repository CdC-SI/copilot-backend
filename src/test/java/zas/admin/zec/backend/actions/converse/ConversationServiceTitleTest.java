package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.AttachmentOcrProperties;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTitleTest {

    private static final String USER = "user";
    private static final String CONVERSATION = "conv";
    private static final List<FAQMessage> EXCHANGE = List.of(
            new FAQMessage("fr", "Quelle est la rente AVS ?", "USER", null, null),
            new FAQMessage("fr", "La rente AVS est...", "FAQ", null, null));

    @Mock
    private ConversationTitleRepository titleRepository;
    @Mock
    private ChatModel chatModel;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        service = new ConversationService(mock(ConversationRepository.class), titleRepository,
                mock(AttachmentRepository.class), mock(ChatServiceFactory.class), chatModel, mock(LlmOcrService.class),
                mock(WorkspaceService.class), mock(ApplicationEventPublisher.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new AttachmentOcrProperties(AttachmentOcrProperties.Engine.PIPELINE, 1000,
                        Duration.ofSeconds(5), Duration.ofHours(1)), new GenerationRegistry());
    }

    @Test
    @DisplayName("creates a placeholder title, then replaces it with the generated one")
    void newConversation_insertsPlaceholderThenUpdatesTitle() {
        var entity = new ConversationTitleEntity();
        when(titleRepository.insertIfAbsent(eq(USER), eq(CONVERSATION), eq("Quelle est la rente AVS ?"), any(), eq("COMPLETE")))
                .thenReturn(1);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Rente AVS")))));
        when(titleRepository.findByUserIdAndConversationId(USER, CONVERSATION)).thenReturn(Optional.of(entity));

        service.initConversation(USER, CONVERSATION, EXCHANGE);

        assertEquals("Rente AVS", entity.getTitle());
        verify(titleRepository).save(entity);
    }

    @Test
    @DisplayName("does not generate a second title for an existing conversation")
    void existingConversation_skipsTitleGeneration() {
        when(titleRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(0);

        service.initConversation(USER, CONVERSATION, EXCHANGE);

        verify(chatModel, never()).call(any(Prompt.class));
        verify(titleRepository, never()).save(any());
    }

    @Test
    @DisplayName("keeps the placeholder title when the LLM fails")
    void llmError_keepsPlaceholder() {
        when(titleRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(1);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("LLM down"));

        assertDoesNotThrow(() -> service.initConversation(USER, CONVERSATION, EXCHANGE));

        verify(titleRepository, never()).save(any());
    }
}
