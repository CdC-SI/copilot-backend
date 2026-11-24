package zas.admin.zec.backend.actions.converse;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.ChatClientConfig;
import zas.admin.zec.backend.config.properties.AttachmentOcrProperties;
import zas.admin.zec.backend.config.properties.ToolApprovalProperties;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;
import zas.admin.zec.backend.prompts.PromptRegistry;
import zas.admin.zec.backend.tools.ToolApprovalService;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Gestion des conversations hors flux de réponse : titres, historique, messages FAQ, suppression, pièces jointes. */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final String USER = "user";
    private static final String CONVERSATION = "conv";

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationTitleRepository titleRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private LlmOcrService ocrService;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ConversationMemory memory;
    @Mock
    private ChatModel chatModel;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        service = new ConversationService(conversationRepository, titleRepository, attachmentRepository,
                mock(ChatServiceFactory.class), ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), ocrService,
                workspaceService, eventPublisher, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new AttachmentOcrProperties(AttachmentOcrProperties.Engine.PIPELINE, 10,
                        Duration.ofSeconds(5), Duration.ofHours(1)), new GenerationRegistry(),
                new ToolApprovalService(new ToolApprovalProperties(List.of(), Duration.ofSeconds(1))),
                new PromptRegistry(), memory);
    }

    @Test
    @DisplayName("sources d'un message : formats étendu et historique relus, liste vide si le message est inconnu")
    void sourcesByMessage() {
        var entity = new MessageEntity();
        entity.setSources(new String[]{"https://x/y", "FILE|memento.pdf|3||in_force|doc-1||1"});
        when(conversationRepository.findByConversationIdAndMessageId(CONVERSATION, "m1")).thenReturn(Optional.of(entity));
        when(conversationRepository.findByConversationIdAndMessageId(CONVERSATION, "inconnu")).thenReturn(Optional.empty());

        var sources = service.getSourcesByMessageUuid(CONVERSATION, "m1");

        assertEquals(new Source(SourceType.URL, "https://x/y"), sources.getFirst());
        assertEquals(new Source("doc-1", SourceType.FILE, "memento.pdf", "3", null, "in_force", null, 1), sources.get(1));
        assertEquals(List.of(), service.getSourcesByMessageUuid(CONVERSATION, "inconnu"));
    }

    @Test
    @DisplayName("titres de l'utilisateur avec leur type de conversation")
    void titles() {
        var timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(titleRepository.findByUserIdOrderByTimestamp(USER))
                .thenReturn(List.of(title("Rente AVS", ConversationType.COMPLETE, timestamp)));

        assertEquals(List.of(new ConversationTitle("Rente AVS", USER, CONVERSATION, ConversationType.COMPLETE, timestamp)),
                service.getTitlesByUserId(USER));
    }

    @Test
    @DisplayName("renommage : titre et horodatage mis à jour ; conversation d'un autre utilisateur ignorée")
    void rename() {
        var entity = title("Ancien", ConversationType.COMPLETE, LocalDateTime.of(2020, 1, 1, 0, 0));
        when(titleRepository.findByUserIdAndConversationId(USER, CONVERSATION)).thenReturn(Optional.of(entity));
        when(titleRepository.findByUserIdAndConversationId("other", CONVERSATION)).thenReturn(Optional.empty());

        service.renameConversation(USER, CONVERSATION, "Nouveau");
        service.renameConversation("other", CONVERSATION, "Intrus");

        assertEquals("Nouveau", entity.getTitle());
        assertTrue(entity.getTimestamp().isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));
        verify(titleRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("suppression : jobs d'extraction annulés, pièces jointes, messages, titre et résumé supprimés")
    void delete() {
        when(attachmentRepository.findPendingJobIdsByConversation(USER, CONVERSATION)).thenReturn(List.of("job-1"));

        service.delete(USER, CONVERSATION);

        verify(eventPublisher).publishEvent(new AttachmentJobsCancelledEvent(USER, List.of("job-1")));
        verify(attachmentRepository).deleteByUserIdAndConversationId(USER, CONVERSATION);
        verify(conversationRepository).deleteByUserIdAndConversationId(USER, CONVERSATION);
        verify(titleRepository).deleteByUserIdAndConversationId(USER, CONVERSATION);
        verify(memory).forget(USER, CONVERSATION);
    }

    @Test
    @DisplayName("suppression sans job d'extraction en cours : aucun événement d'annulation")
    void delete_withoutPendingJobs() {
        when(attachmentRepository.findPendingJobIdsByConversation(USER, CONVERSATION)).thenReturn(List.of());

        service.delete(USER, CONVERSATION);

        verifyNoInteractions(eventPublisher);
        verify(conversationRepository).deleteByUserIdAndConversationId(USER, CONVERSATION);
    }

    @Test
    @DisplayName("FAQ : messages enregistrés avec leurs sources dédoublonnées, relus avec le rôle FAQ")
    void initConversation_savesFaqMessagesAndHistoryExposesFaqRole() {
        var faqId = UUID.randomUUID();
        var source = new Source(SourceType.URL, "https://x/faq");
        when(titleRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(0);

        service.initConversation(USER, CONVERSATION, List.of(
                new FAQMessage("de", "Frage ?", "USER", null, null),
                new FAQMessage("de", "Antwort.", "FAQ", faqId, List.of(source, source))));

        var captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(conversationRepository, times(2)).save(captor.capture());
        var question = captor.getAllValues().getFirst();
        var answer = captor.getAllValues().get(1);
        assertEquals("user", question.getRole());
        assertArrayEquals(new String[0], question.getSources());
        assertEquals("assistant", answer.getRole());
        assertEquals(faqId, answer.getFaqId());
        assertEquals("de", answer.getLanguage());
        assertArrayEquals(new String[]{"https://x/faq"}, answer.getSources());

        when(conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(eq(CONVERSATION), eq(USER), any()))
                .thenReturn(List.of(question, answer));
        var history = service.getByConversationIdAndUserId(CONVERSATION, USER).messages();
        assertEquals(List.of("USER", "FAQ"), history.stream().map(Message::role).toList());
        assertEquals(List.of(source), history.get(1).sources());
    }

    @Test
    @DisplayName("titre provisoire tronqué à 100 caractères ; titre généré vide : titre provisoire conservé")
    void placeholderTitleIsTruncatedAndBlankGeneratedTitleIgnored() {
        String longQuestion = "  " + "a".repeat(150) + "  ";
        when(titleRepository.insertIfAbsent(eq(USER), eq(CONVERSATION), any(), any(), eq("COMPLETE"))).thenReturn(1);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("   ")))));

        service.initConversation(USER, CONVERSATION, List.of(
                new FAQMessage("fr", longQuestion, "USER", null, null),
                new FAQMessage("fr", "Réponse [^1].", "FAQ", null, null)));

        var placeholder = ArgumentCaptor.forClass(String.class);
        verify(titleRepository).insertIfAbsent(eq(USER), eq(CONVERSATION), placeholder.capture(), any(), any());
        assertEquals("a".repeat(100) + "…", placeholder.getValue());
        verify(titleRepository, never()).findByUserIdAndConversationId(any(), any());
        verify(titleRepository, never()).save(any());
    }

    @Test
    @DisplayName("ajout de messages FAQ : uniquement à une conversation existante de l'utilisateur")
    void update() {
        when(titleRepository.findByUserIdAndConversationId(USER, CONVERSATION))
                .thenReturn(Optional.of(title("T", ConversationType.COMPLETE, LocalDateTime.now())));
        when(titleRepository.findByUserIdAndConversationId(USER, "inconnue")).thenReturn(Optional.empty());
        var message = new FAQMessage("fr", "Q ?", "USER", null, null);

        service.update(USER, CONVERSATION, List.of(message));
        service.update(USER, "inconnue", List.of(message));

        var captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals(CONVERSATION, captor.getValue().getConversationId());
        assertEquals(USER, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("statut agrégé des pièces jointes : FAILED > PENDING > PROCESSED")
    void attachmentsStatus() {
        when(attachmentRepository.findAllByConversationIdAndUserId(CONVERSATION, USER)).thenReturn(List.of(
                attachment(1L, AttachmentStatus.PROCESSED, null), attachment(2L, AttachmentStatus.PENDING, null)));

        var response = service.getAttachmentsStatus(CONVERSATION, USER);

        assertEquals(AttachmentStatus.PENDING, response.status());
        assertEquals(AttachmentStatus.PENDING.getDescription(), response.message());
        assertEquals(List.of(1L, 2L), response.conversationAttachments().attachments().stream().map(Attachment::id).toList());

        when(attachmentRepository.findAllByConversationIdAndUserId(CONVERSATION, USER)).thenReturn(List.of(
                attachment(1L, AttachmentStatus.PENDING, null), attachment(2L, AttachmentStatus.FAILED, UploadErrorCode.OCR_FAILED)));
        var failed = service.getAttachmentsStatus(CONVERSATION, USER);
        assertEquals(AttachmentStatus.FAILED, failed.status());
        assertEquals(UploadErrorCode.OCR_FAILED, failed.conversationAttachments().attachments().get(1).errorCode());

        when(attachmentRepository.findAllByConversationIdAndUserId(CONVERSATION, USER))
                .thenReturn(List.of(attachment(1L, AttachmentStatus.PROCESSED, null)));
        assertEquals(AttachmentStatus.PROCESSED, service.getAttachmentsStatus(CONVERSATION, USER).status());
    }

    @Test
    @DisplayName("téléchargement d'une pièce jointe de l'utilisateur ; introuvable sinon")
    void getAttachment() throws IOException {
        var entity = attachment(3L, AttachmentStatus.PROCESSED, null);
        entity.setFileBytes("PDF".getBytes());
        when(attachmentRepository.findByIdAndConversationIdAndUserId(3L, CONVERSATION, USER)).thenReturn(Optional.of(entity));
        when(attachmentRepository.findByIdAndConversationIdAndUserId(4L, CONVERSATION, USER)).thenReturn(Optional.empty());

        var doc = service.getAttachment(CONVERSATION, 3L, USER);

        assertEquals("file-3.pdf", doc.filename());
        assertArrayEquals("PDF".getBytes(), doc.content().getContentAsByteArray());
        assertThrows(NoSuchElementException.class, () -> service.getAttachment(CONVERSATION, 4L, USER));
    }

    @Test
    @DisplayName("pièce jointe sans conversation : nouvel identifiant de conversation généré")
    void attach_withoutConversation_generatesConversationId() throws IOException {
        when(ocrService.isSupported(any())).thenReturn(true);
        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            AttachmentEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return entity;
        });

        var result = service.attachFilesToConversation(null, USER,
                List.of(new MockMultipartFile("files", "photo.png", "image/png", new byte[]{1, 2})));

        assertDoesNotThrow(() -> UUID.fromString(result.conversationId()));
        var captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository).save(captor.capture());
        assertEquals(result.conversationId(), captor.getValue().getConversationId());
        assertNull(captor.getValue().getNextPollAt(), "image : OCR LLM, pas de pipeline");
    }

    @Test
    @DisplayName("fichier illisible : FILE_READ_ERROR")
    void attach_unreadableFile() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("broken.pdf");
        when(file.getBytes()).thenThrow(new IOException("disk"));

        var ex = assertThrows(UploadException.class,
                () -> service.attachFilesToConversation(CONVERSATION, USER, List.of(file)));

        assertEquals(UploadErrorCode.FILE_READ_ERROR, ex.getErrorCode());
        assertEquals("broken.pdf", ex.getFileName());
    }

    @Test
    @DisplayName("échec de persistance : UPLOAD_FAILED et aucun traitement déclenché")
    void attach_persistenceFailure() throws IOException {
        when(ocrService.isSupported(any())).thenReturn(true);
        when(attachmentRepository.save(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        var ex = assertThrows(UploadException.class, () -> service.attachFilesToConversation(CONVERSATION, USER,
                List.of(new MockMultipartFile("files", "photo.png", "image/png", new byte[]{1}))));

        assertEquals(UploadErrorCode.UPLOAD_FAILED, ex.getErrorCode());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("workspaces disponibles")
    void workspaces() {
        when(workspaceService.getAllNames()).thenReturn(List.of("AVS", "RH"));

        assertEquals(List.of("AVS", "RH"), service.getWorkspaces());
    }

    @Test
    @DisplayName("dernier échange : aucun message utilisateur, aucun identifiant")
    void lastExchangeWithoutQuestion() {
        var faq = new Message("f1", USER, CONVERSATION, null, "fr", "Bienvenue", "FAQ", null, null, LocalDateTime.now(), null);

        assertEquals(-1, ConversationService.lastExchangeStart(List.of(faq)));
        assertEquals(List.of(), ConversationService.lastExchangeMessageIds(List.of(faq)));
        assertEquals(List.of(), ConversationService.lastExchangeMessageIds(List.of()));
    }

    private static ConversationTitleEntity title(String title, ConversationType type, LocalDateTime timestamp) {
        var entity = new ConversationTitleEntity();
        entity.setTitle(title);
        entity.setUserId(USER);
        entity.setConversationId(CONVERSATION);
        entity.setConversationType(type);
        entity.setTimestamp(timestamp);
        return entity;
    }

    private static AttachmentEntity attachment(Long id, AttachmentStatus status, UploadErrorCode errorCode) {
        var entity = new AttachmentEntity();
        entity.setId(id);
        entity.setFilename("file-" + id + ".pdf");
        entity.setFileSize(10L);
        entity.setStatus(status);
        entity.setErrorCode(errorCode);
        return entity;
    }
}
