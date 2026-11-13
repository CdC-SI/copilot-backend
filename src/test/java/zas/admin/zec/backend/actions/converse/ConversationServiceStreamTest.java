package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.AttachmentOcrProperties;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.StatusToken;
import zas.admin.zec.backend.rag.token.SuggestionToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.token.WorkspaceToken;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F3/F3b : enregistrement de la réponse interrompue (Stop, déconnexion), remplacement du dernier échange
 * (régénération / édition) et sérialisation des générations dans {@link ConversationService#streamAnswer}.
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

    private final GenerationRegistry registry = new GenerationRegistry();
    private ConversationService service;
    private final List<ChatStreamEvent> emitted = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        lenient().when(chatServiceFactory.resolve(any())).thenReturn(chatService);
        service = new ConversationService(conversationRepository, titleRepository,
                mock(AttachmentRepository.class), chatServiceFactory, chatModel, mock(LlmOcrService.class),
                mock(WorkspaceService.class), mock(ApplicationEventPublisher.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new AttachmentOcrProperties(AttachmentOcrProperties.Engine.PIPELINE, 1000,
                        Duration.ofSeconds(5), Duration.ofHours(1)), registry);
    }

    @Test
    @DisplayName("fin normale : start, contenu, puis done émis après l'enregistrement de la question et de la réponse")
    void normalCompletion_savesFullAnswer() {
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.just(text("Bon"), text("jour")));
        when(conversationRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            // done ne doit pas encore avoir été émis au moment de l'enregistrement.
            assertTrue(emitted.stream().noneMatch(e -> e.type().equals(ChatStreamEvent.DONE)));
            return invocation.getArgument(0);
        });

        var output = service.streamAnswer(question(null), USER).doOnNext(emitted::add).collectList().block(ASYNC);

        assertEquals(List.of("start", "text_delta", "text_delta", "done"), types(output));
        var start = (ChatStreamEvent.Start) output.get(0).data();
        assertEquals(CONVERSATION, start.conversationId());
        assertEquals(new ChatStreamEvent.TextDelta("Bon"), output.get(1).data());
        assertEquals(new ChatStreamEvent.Done(start.messageId(), false), output.get(3).data());
        var saved = savedMessages(2);
        assertEquals("user", saved.get(0).getRole());
        assertEquals("Question ?", saved.get(0).getMessage());
        assertFalse(saved.get(0).isInterrupted());
        assertEquals("assistant", saved.get(1).getRole());
        assertEquals(start.messageId(), saved.get(1).getMessageId());
        assertEquals("Bonjour", saved.get(1).getMessage());
        assertFalse(saved.get(1).isInterrupted());
        verify(conversationRepository, never()).deleteByUserIdAndConversationIdAndMessageIdIn(any(), any(), any());
    }

    @Test
    @DisplayName("statut, source, suggestion et workspace : événements typés, sources et suggestions dédoublonnées")
    void tokens_areMappedToTypedEvents() {
        var source = SourceToken.fromFileWithDetails("doc-1", "Mémento.pdf", "2", "", "in_force");
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.just(
                new StatusToken(ChatStatus.RETRIEVAL, "fr"), new WorkspaceToken("AVS"), source, source,
                new SuggestionToken("Et ensuite ?"), new SuggestionToken("Et ensuite ?"), text("Réponse")));

        var output = service.streamAnswer(question(null), USER).collectList().block(ASYNC);

        assertEquals(List.of("start", "status", "workspace", "source", "suggestion", "text_delta", "done"), types(output));
        assertEquals(new ChatStreamEvent.Status("retrieval", "Recherche des documents pertinents"), output.get(1).data());
        assertEquals(new ChatStreamEvent.Workspace("AVS"), output.get(2).data());
        assertEquals(Source.fromToken(source), output.get(3).data());
        assertEquals(new ChatStreamEvent.Suggestion("Et ensuite ?"), output.get(4).data());
    }

    @Test
    @DisplayName("nouvelle conversation : titre provisoire avant done, titre généré après done, puis fin du flux")
    void newConversation_generatesTitleAfterDone() {
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.just(text("Réponse")));
        when(titleRepository.insertIfAbsent(eq(USER), eq(CONVERSATION), eq("Question ?"), any(), any())).thenReturn(1);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            assertEquals(ChatStreamEvent.DONE, emitted.getLast().type());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Titre"))));
        });
        var title = new ConversationTitleEntity();
        when(titleRepository.findByUserIdAndConversationId(USER, CONVERSATION)).thenReturn(Optional.of(title));

        var output = service.streamAnswer(question(null), USER).doOnNext(emitted::add).collectList().block(ASYNC);

        assertEquals(ChatStreamEvent.DONE, output.getLast().type());
        assertEquals("Titre", title.getTitle());
    }

    @Test
    @DisplayName("déconnexion : annulation propagée au LLM, question + réponse partielle enregistrées une seule fois, marquée interrompue")
    void cancel_savesPartialInterruptedAnswerOnce() throws InterruptedException {
        var llmCancelled = new CountDownLatch(1);
        when(chatService.answer(any(), eq(USER), any())).thenReturn(
                Flux.concat(Flux.just(text("Réponse "), text("partielle")), Flux.<Token>never())
                        .doOnCancel(llmCancelled::countDown));

        // take(3) annule l'abonnement après start et les deux premiers tokens, comme une déconnexion du client.
        var output = service.streamAnswer(question(null), USER).take(3).collectList().block(ASYNC);

        assertEquals(List.of("start", "text_delta", "text_delta"), types(output));

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
    @DisplayName("déconnexion avant le premier token : question + réponse vide interrompue enregistrées")
    void cancelBeforeAnyToken_savesEmptyInterruptedAnswer() {
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.never());
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(List.of());

        var subscription = service.streamAnswer(question(null), USER).subscribe();
        verify(chatService, timeout(ASYNC.toMillis())).answer(any(), any(), any());
        subscription.dispose();

        var saved = savedMessages(2);
        assertEquals("Question ?", saved.get(0).getMessage());
        assertEquals("", saved.get(1).getMessage());
        assertTrue(saved.get(1).isInterrupted());
    }

    @Test
    @DisplayName("régénération : dernier échange exclu du contexte puis supprimé dans la transaction d'enregistrement")
    void regenerate_excludesAndDeletesLastExchange() {
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(history());
        var context = new AtomicReference<List<Message>>();
        when(chatService.answer(any(), eq(USER), any())).thenAnswer(invocation -> {
            context.set(invocation.getArgument(2));
            return Flux.just(text("Nouvelle réponse"));
        });

        service.streamAnswer(question("a2"), USER).collectList().block(ASYNC);

        assertEquals(List.of("u1", "a1"), context.get().stream().map(Message::messageId).toList());
        var inOrder = inOrder(conversationRepository);
        inOrder.verify(conversationRepository).deleteByUserIdAndConversationIdAndMessageIdIn(USER, CONVERSATION, List.of("u2", "a2"));
        inOrder.verify(conversationRepository, times(2)).save(any(MessageEntity.class));
        var saved = savedMessages(2);
        assertEquals("Nouvelle réponse", saved.get(1).getMessage());
        assertFalse(saved.get(1).isInterrupted());
    }

    @Test
    @DisplayName("régénération + déconnexion : dernier échange supprimé et réponse partielle enregistrée interrompue")
    void regenerate_cancel_deletesLastExchange() {
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(history());
        when(chatService.answer(any(), eq(USER), any()))
                .thenReturn(Flux.concat(Flux.just(text("Début")), Flux.<Token>never()));

        assertEquals(List.of("start", "text_delta"),
                types(service.streamAnswer(question("a2"), USER).take(2).collectList().block(ASYNC)));

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

        var output = service.streamAnswer(question("a2"), USER).collectList().block(ASYNC);

        assertEquals(List.of("start", "text_delta", "error"), types(output));
        assertEquals(new ChatStreamEvent.Error(ChatStreamEvent.GENERATION_FAILED, "boom"), output.get(2).data());
        verify(conversationRepository, never()).deleteByUserIdAndConversationIdAndMessageIdIn(any(), any(), any());
        verify(conversationRepository, never()).save(any(MessageEntity.class));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"a1", "u2", "inconnu"})
    @DisplayName("régénération d'un message qui n'est pas la dernière réponse : 409 avant tout streaming, registre libéré")
    void regenerate_notLastAnswer_isRejected(String replaceMessageId) {
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(history());

        var conflict = assertThrows(ConversationConflictException.class,
                () -> service.streamAnswer(question(replaceMessageId), USER));

        assertEquals(ConversationConflictException.TURN_NOT_REPLACEABLE, conflict.getCode());
        verifyNoInteractions(chatService);
        assertDoesNotThrow(() -> registry.finish(registry.begin("next", USER, CONVERSATION)));
    }

    @Test
    @DisplayName("régénération sans échange précédent : 409")
    void regenerate_withoutPreviousExchange_isRejected() {
        var conflict = assertThrows(ConversationConflictException.class,
                () -> service.streamAnswer(question("a2"), USER));

        assertEquals(ConversationConflictException.TURN_NOT_REPLACEABLE, conflict.getCode());
    }

    @Test
    @DisplayName("Stop : production arrêtée, réponse partielle enregistrée puis done {interrupted: true}, registre libéré")
    void stop_savesPartialAnswerThenEmitsInterruptedDone() {
        var llmCancelled = new CountDownLatch(1);
        when(chatService.answer(any(), eq(USER), any())).thenReturn(
                Flux.concat(Flux.just(text("Réponse "), text("partielle")), Flux.<Token>never())
                        .doOnCancel(llmCancelled::countDown));
        when(conversationRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            assertTrue(emitted.stream().noneMatch(e -> e.type().equals(ChatStreamEvent.DONE)));
            return invocation.getArgument(0);
        });

        var output = service.streamAnswer(question(null), USER)
                .doOnNext(emitted::add)
                .doOnNext(event -> {
                    if (emitted.size() == 3) {
                        var start = (ChatStreamEvent.Start) emitted.getFirst().data();
                        assertTrue(service.stopGeneration(USER, CONVERSATION, start.messageId()));
                    }
                })
                .collectList().block(ASYNC);

        assertEquals(List.of("start", "text_delta", "text_delta", "done"), types(output));
        var start = (ChatStreamEvent.Start) output.getFirst().data();
        assertEquals(new ChatStreamEvent.Done(start.messageId(), true), output.getLast().data());
        assertEquals(0, llmCancelled.getCount(), "le Stop doit annuler l'appel LLM");
        var saved = savedMessages(2);
        assertEquals("Réponse partielle", saved.get(1).getMessage());
        assertTrue(saved.get(1).isInterrupted());
        assertFalse(service.stopGeneration(USER, CONVERSATION, start.messageId()), "génération terminée");
        assertDoesNotThrow(() -> registry.finish(registry.begin("next", USER, CONVERSATION)));
    }

    @Test
    @DisplayName("Stop : refusé pour un autre utilisateur, une autre conversation ou une génération inconnue")
    void stop_otherOwnerOrUnknown_isRejected() {
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.never());

        var subscription = service.streamAnswer(question(null), USER).doOnNext(emitted::add).subscribe();
        try {
            var messageId = ((ChatStreamEvent.Start) emitted.getFirst().data()).messageId();
            assertFalse(service.stopGeneration("other", CONVERSATION, messageId));
            assertFalse(service.stopGeneration(USER, "other", messageId));
            assertFalse(service.stopGeneration(USER, CONVERSATION, "inconnu"));
        } finally {
            subscription.dispose();
        }
    }

    @Test
    @DisplayName("nouvelle question pendant qu'une réponse est produite : 409 GENERATION_IN_PROGRESS")
    void secondGeneration_whileStreaming_isRejected() {
        when(chatService.answer(any(), eq(USER), any())).thenReturn(Flux.never());

        var subscription = service.streamAnswer(question(null), USER).subscribe();
        try {
            var conflict = assertThrows(ConversationConflictException.class,
                    () -> service.streamAnswer(question(null), USER));
            assertEquals(ConversationConflictException.GENERATION_IN_PROGRESS, conflict.getCode());
        } finally {
            subscription.dispose();
        }
    }

    @Test
    @DisplayName("régénération pendant l'enregistrement de la réponse interrompue : attend le commit avant de relire l'historique")
    void regenerate_whileSaving_waitsForCommit() throws Exception {
        var release = new CountDownLatch(1);
        var saved = new CopyOnWriteArrayList<MessageEntity>();
        when(conversationRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            assertTrue(release.await(ASYNC.toMillis(), TimeUnit.MILLISECONDS));
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenAnswer(invocation -> List.copyOf(saved));
        when(chatService.answer(any(), eq(USER), any()))
                .thenReturn(Flux.concat(Flux.just(text("Début")), Flux.<Token>never()))
                .thenReturn(Flux.just(text("Nouvelle réponse")));

        // Déconnexion après le premier token : l'enregistrement (bloqué) démarre.
        var first = service.streamAnswer(question(null), USER).take(2).collectList().block(ASYNC);
        var interruptedId = ((ChatStreamEvent.Start) first.getFirst().data()).messageId();

        var regenerate = CompletableFuture.supplyAsync(() -> service.streamAnswer(question(interruptedId), USER));
        Thread.sleep(200);
        assertFalse(regenerate.isDone(), "la régénération doit attendre la fin de l'enregistrement");

        release.countDown();
        var output = regenerate.get(ASYNC.toMillis(), TimeUnit.MILLISECONDS).collectList().block(ASYNC);

        assertEquals(ChatStreamEvent.DONE, output.getLast().type());
        verify(conversationRepository).deleteByUserIdAndConversationIdAndMessageIdIn(eq(USER), eq(CONVERSATION),
                argThat(ids -> ids.contains(interruptedId) && ids.size() == 2));
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

    private static Question question(String replaceMessageId) {
        return Question.builder()
                .query("Question ?")
                .language("fr")
                .conversationId(CONVERSATION)
                .ragEnabled(true)
                .replaceMessageId(replaceMessageId)
                .build()
                .withDefaults();
    }

    private static Token text(String content) {
        return new TextToken(content);
    }

    private static List<String> types(List<ChatStreamEvent> events) {
        return events.stream().map(ChatStreamEvent::type).toList();
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
