package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceAttachmentTest {

    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private LlmOcrService ocrService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(mock(ConversationRepository.class), mock(ConversationTitleRepository.class),
                attachmentRepository, mock(ChatServiceFactory.class), mock(ChatModel.class), ocrService,
                mock(WorkspaceService.class), eventPublisher,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    @DisplayName("rejects a request without any file with NO_FILE")
    void attach_rejectsMissingFiles() {
        var ex = assertThrows(UploadException.class, () -> service.attachFilesToConversation("conv", "user", List.of()));

        assertEquals(UploadErrorCode.NO_FILE, ex.getErrorCode());
        verifyNoInteractions(attachmentRepository, eventPublisher);
    }

    @Test
    @DisplayName("rejects an empty file with FILE_CONTENT_INVALID")
    void attach_rejectsEmptyFile() {
        var file = new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]);

        var ex = assertThrows(UploadException.class, () -> service.attachFilesToConversation("conv", "user", List.of(file)));

        assertEquals(UploadErrorCode.FILE_CONTENT_INVALID, ex.getErrorCode());
        assertEquals("empty.pdf", ex.getFileName());
        verifyNoInteractions(attachmentRepository, eventPublisher);
    }

    @Test
    @DisplayName("rejects a file not supported by the OCR with FILE_TYPE_UNSUPPORTED")
    void attach_rejectsUnsupportedType() throws IOException {
        var file = new MockMultipartFile("files", "doc.docx", "application/octet-stream", "data".getBytes());
        when(ocrService.isSupported(any())).thenReturn(false);

        var ex = assertThrows(UploadException.class, () -> service.attachFilesToConversation("conv", "user", List.of(file)));

        assertEquals(UploadErrorCode.FILE_TYPE_UNSUPPORTED, ex.getErrorCode());
        assertEquals("doc.docx", ex.getFileName());
        verifyNoInteractions(attachmentRepository, eventPublisher);
    }

    @Test
    @DisplayName("persists supported files as PENDING and triggers the OCR")
    void attach_persistsSupportedFiles() throws IOException {
        var file = new MockMultipartFile("files", "doc.pdf", "application/pdf", "%PDF".getBytes());
        when(ocrService.isSupported(any())).thenReturn(true);
        when(attachmentRepository.save(any())).thenAnswer(inv -> {
            AttachmentEntity entity = inv.getArgument(0);
            entity.setId(42L);
            return entity;
        });

        var result = service.attachFilesToConversation("conv", "user", List.of(file));

        assertEquals("conv", result.conversationId());
        assertEquals(List.of(new Attachment(42L, "doc.pdf", 4L, AttachmentStatus.PENDING, null)), result.attachments());
        verify(eventPublisher).publishEvent(new AttachmentUploadedEvent(42L));
    }

    @Test
    @DisplayName("maps OCR failures to explicit error codes")
    void toErrorCode_mapsOcrFailures() {
        assertEquals(UploadErrorCode.FILE_TYPE_UNSUPPORTED,
                AttachmentAsyncProcessor.toErrorCode(new LlmOcrService.UnsupportedFileTypeException("text/plain")));
        assertEquals(UploadErrorCode.FILE_CONTENT_INVALID,
                AttachmentAsyncProcessor.toErrorCode(new IOException("corrupted")));
        assertEquals(UploadErrorCode.OCR_FAILED,
                AttachmentAsyncProcessor.toErrorCode(new RuntimeException("vision model down")));
    }
}
