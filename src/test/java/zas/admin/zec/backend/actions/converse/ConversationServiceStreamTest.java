package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.AttachmentOcrProperties;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F3 : enregistrement de la réponse interrompue (Stop) et remplacement du dernier échange
 * (régénération / édition) dans {@link ConversationService#streamAnswer}.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceStreamTest {

    private static final String USER = "user";
    private static final String CONVERSATION = "conv";
    private static final Duration ASYNC = Duration.ofSeconds(5);

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationTitleRepository titleRepository;
    @Mock
    private ChatServiceFactory chatServiceFactory;
    @Mock
    private ChatService chatService;
    @Mock
    private ChatModel chatModel;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        lenient().when(chatServiceFactory.resolve(any())).thenReturn(chatService);
        service = new ConversationService(conversationRepository, titleRepository,
                mock(AttachmentRepository.class), chatServiceFactory, chatModel, mock(LlmOcrService.class),
                mock(WorkspaceService.class), mock(ApplicationEventPublisher.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new AttachmentOcrProperties(AttachmentOcrProperties.Engine.PIPELINE, 1000,
                        Duration.ofSeconds(5), Duration.ofHours(1)));
    }

    @Test
    @DisplayName("fin normale : question + réponse complète enregistrées, non interrompue, rien supprimé")
    void normalCompletion_savesFullAnswer() {
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.just(text("Bon"), text("jour")));

        var output = service.streamAnswer(question(false), USER).collectList().block(ASYNC);

        assertEquals("Bon", output.get(0));
        assertEquals("jour", output.get(1));
        assertTrue(output.get(2).startsWith("<message_uuid>"));
        var saved = savedMessages(2);
        assertEquals("user", saved.get(0).getRole());
        assertEquals("Question ?", saved.get(0).getMessage());
        assertFalse(saved.get(0).isInterrupted());
        assertEquals("assistant", saved.get(1).getRole());
        assertEquals("Bonjour", saved.get(1).getMessage());
        assertFalse(saved.get(1).isInterrupted());
        verify(conversationRepository, never()).deleteByUserIdAndConversationIdAndMessageIdIn(any(), any(), any());
    }

    @Test
    @DisplayName("Stop : annulation propagée au LLM, question + réponse partielle enregistrées une seule fois, marquée interrompue")
    void cancel_savesPartialInterruptedAnswerOnce() throws InterruptedException {
        var llmCancelled = new CountDownLatch(1);
        when(chatService.answer(any(), eq(USER), any())).thenReturn(
                Flux.concat(Flux.just(text("Réponse "), text("partielle")), Flux.<Token>never())
                        .doOnCancel(llmCancelled::countDown));

        // take(2) annule l'abonnement après les deux premiers tokens, comme un Stop côté client.
        var output = service.streamAnswer(question(false), USER).take(2).collectList().block(ASYNC);

        assertEquals(List.of("Réponse ", "partielle"), output);

        // L'annulation peut être relayée par le thread émetteur juste après la fin de take(2).
        assertTrue(llmCancelled.await(ASYNC.toMillis(), TimeUnit.MILLISECONDS), "l'annulation doit remonter jusqu'à l'appel LLM");
        var saved = savedMessages(2);
        assertEquals("Question ?", saved.get(0).getMessage());
        assertFalse(saved.get(0).isInterrupted());
        assertEquals("Réponse partielle", saved.get(1).getMessage());
        assertTrue(saved.get(1).isInterrupted());
        Thread.sleep(200);
        verify(conversationRepository, times(2)).save(any(MessageEntity.class));
    }

    @Test
    @DisplayName("Stop avant le premier token : question + réponse vide interrompue enregistrées")
    void cancelBeforeAnyToken_savesEmptyInterruptedAnswer() {
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.never());
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(List.of());

        var subscription = service.streamAnswer(question(false), USER).subscribe();
        verify(chatService, timeout(ASYNC.toMillis())).answer(any(), any(), any());
        subscription.dispose();

        var saved = savedMessages(2);
        assertEquals("Question ?", saved.get(0).getMessage());
        assertEquals("", saved.get(1).getMessage());
        assertTrue(saved.get(1).isInterrupted());
    }

    @Test
    @DisplayName("replaceLastTurn : dernier échange exclu du contexte puis supprimé dans la transaction d'enregistrement")
    void replaceLastTurn_excludesAndDeletesLastExchange() {
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(history());
        var context = new AtomicReference<List<Message>>();
        when(chatService.answer(any(), eq(USER), any())).thenAnswer(invocation -> {
            context.set(invocation.getArgument(2));
            return Flux.just(text("Nouvelle réponse"));
        });

        service.streamAnswer(question(true), USER).collectList().block(ASYNC);

        assertEquals(List.of("u1", "a1"), context.get().stream().map(Message::messageId).toList());
        var inOrder = inOrder(conversationRepository);
        inOrder.verify(conversationRepository).deleteByUserIdAndConversationIdAndMessageIdIn(USER, CONVERSATION, List.of("u2", "a2"));
        inOrder.verify(conversationRepository, times(2)).save(any(MessageEntity.class));
        var saved = savedMessages(2);
        assertEquals("Nouvelle réponse", saved.get(1).getMessage());
        assertFalse(saved.get(1).isInterrupted());
    }

    @Test
    @DisplayName("replaceLastTurn + Stop : dernier échange supprimé et réponse partielle enregistrée interrompue")
    void replaceLastTurn_cancel_deletesLastExchange() {
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(history());
        when(chatService.answer(any(), eq(USER), any()))
                .thenReturn(Flux.concat(Flux.just(text("Début")), Flux.<Token>never()));

        assertEquals(List.of("Début"), service.streamAnswer(question(true), USER).take(1).collectList().block(ASYNC));

        verify(conversationRepository, timeout(ASYNC.toMillis()))
                .deleteByUserIdAndConversationIdAndMessageIdIn(USER, CONVERSATION, List.of("u2", "a2"));
        var saved = savedMessages(2);
        assertEquals("Début", saved.get(1).getMessage());
        assertTrue(saved.get(1).isInterrupted());
    }

    @Test
    @DisplayName("erreur : rien n'est enregistré et l'échange à remplacer est conservé")
    void error_keepsPreviousExchange() {
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(history());
        when(chatService.answer(any(), eq(USER), any()))
                .thenReturn(Flux.concat(Flux.just(text("Début")), Flux.error(new IllegalStateException("boom"))));

        var output = service.streamAnswer(question(true), USER).collectList().block(ASYNC);

        assertEquals(List.of("Début", "<error>boom</error>"), output);
        verify(conversationRepository, never()).deleteByUserIdAndConversationIdAndMessageIdIn(any(), any(), any());
        verify(conversationRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    @DisplayName("replaceLastTurn sans échange précédent : comportement normal")
    void replaceLastTurn_withoutPreviousExchange_behavesNormally() {
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(List.of());
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.just(text("Réponse")));

        service.streamAnswer(question(true), USER).collectList().block(ASYNC);

        verify(conversationRepository, never()).deleteByUserIdAndConversationIdAndMessageIdIn(any(), any(), any());
        assertEquals("Réponse", savedMessages(2).get(1).getMessage());
    }

    @Test
    @DisplayName("historique : le flag interrupted est exposé sur les messages de la conversation")
    void history_exposesInterruptedFlag() {
        var interrupted = entity("a1", "assistant", 2);
        interrupted.setInterrupted(true);
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(List.of(entity("u1", "user", 1), interrupted));

        var messages = service.getByConversationIdAndUserId(CONVERSATION, USER).messages();

        assertFalse(messages.get(0).interrupted());
        assertTrue(messages.get(1).interrupted());
    }

    private static Question question(boolean replaceLastTurn) {
        return Question.builder()
                .query("Question ?")
                .language("fr")
                .conversationId(CONVERSATION)
                .ragEnabled(true)
                .replaceLastTurn(replaceLastTurn)
                .build()
                .withDefaults();
    }

    private static Token text(String content) {
        return new TextToken(content);
    }

    private List<MessageEntity> savedMessages(int expected) {
        var captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(conversationRepository, timeout(ASYNC.toMillis()).times(expected)).save(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private static List<MessageEntity> history() {
        return List.of(entity("u1", "user", 1), entity("a1", "assistant", 2),
                entity("u2", "user", 3), entity("a2", "assistant", 4));
    }

    private static MessageEntity entity(String messageId, String role, int minute) {
        var entity = new MessageEntity();
        entity.setMessageId(messageId);
        entity.setUserId(USER);
        entity.setConversationId(CONVERSATION);
        entity.setRole(role);
        entity.setMessage(messageId);
        entity.setLanguage("fr");
        entity.setTimestamp(LocalDateTime.of(2026, 1, 1, 10, minute));
        entity.setSources(new String[0]);
        entity.setSuggestions(new String[0]);
        return entity;
    }
}
