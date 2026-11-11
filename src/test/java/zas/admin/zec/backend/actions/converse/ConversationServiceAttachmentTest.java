package zas.admin.zec.backend.actions.converse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import zas.admin.zec.backend.config.properties.AttachmentOcrProperties;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new AttachmentOcrProperties(AttachmentOcrProperties.Engine.PIPELINE, 2,
                        Duration.ofSeconds(5), Duration.ofHours(1)));
    }

    private static byte[] pdfWithPages(int pages) throws IOException {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private void givenSavedWithId(long id) {
        when(attachmentRepository.save(any())).thenAnswer(inv -> {
            AttachmentEntity entity = inv.getArgument(0);
            entity.setId(id);
            return entity;
        });
    }

    @Test
    @DisplayName("routes PDFs to the pipeline poller (next_poll_at set)")
    void attach_routesPdfToPipeline() throws IOException {
        var file = new MockMultipartFile("files", "doc.pdf", "application/pdf", pdfWithPages(2));
        when(ocrService.isSupported(any())).thenReturn(true);
        when(ocrService.isPdf(any())).thenReturn(true);
        givenSavedWithId(7L);

        service.attachFilesToConversation("conv", "user", List.of(file));

        var captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository).save(captor.capture());
        assertNotNull(captor.getValue().getNextPollAt());
        verify(eventPublisher).publishEvent(new AttachmentUploadedEvent(7L, true));
    }

    @Test
    @DisplayName("rejects a PDF above the page limit with TOO_MANY_PAGES")
    void attach_rejectsTooManyPages() throws IOException {
        var file = new MockMultipartFile("files", "big.pdf", "application/pdf", pdfWithPages(3));
        when(ocrService.isSupported(any())).thenReturn(true);
        when(ocrService.isPdf(any())).thenReturn(true);

        var ex = assertThrows(UploadException.class, () -> service.attachFilesToConversation("conv", "user", List.of(file)));

        assertEquals(UploadErrorCode.TOO_MANY_PAGES, ex.getErrorCode());
        assertEquals("big.pdf", ex.getFileName());
        verifyNoInteractions(attachmentRepository, eventPublisher);
    }

    @Test
    @DisplayName("rejects an unreadable PDF with FILE_CONTENT_INVALID")
    void attach_rejectsUnreadablePdf() throws IOException {
        var file = new MockMultipartFile("files", "broken.pdf", "application/pdf", "%PDF-garbage".getBytes());
        when(ocrService.isSupported(any())).thenReturn(true);
        when(ocrService.isPdf(any())).thenReturn(true);

        var ex = assertThrows(UploadException.class, () -> service.attachFilesToConversation("conv", "user", List.of(file)));

        assertEquals(UploadErrorCode.FILE_CONTENT_INVALID, ex.getErrorCode());
        verifyNoInteractions(attachmentRepository, eventPublisher);
    }

    @Test
    @DisplayName("cancels pending pipeline jobs when an attachment is deleted")
    void deleteAttachment_cancelsPendingJob() {
        when(attachmentRepository.findPendingJobIds(3L, "user")).thenReturn(List.of("job-3"));

        service.deleteAttachment(3L, "user");

        verify(eventPublisher).publishEvent(new AttachmentJobsCancelledEvent("user", List.of("job-3")));
        verify(attachmentRepository).deleteByIdAndUserId(3L, "user");
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
        verify(eventPublisher).publishEvent(new AttachmentUploadedEvent(42L, false));
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
