package zas.admin.zec.backend.actions.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.upload.model.*;
import zas.admin.zec.backend.actions.upload.strategy.AdminDocUploadStrategyFactory;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private AdminDocUploadStrategyFactory adminDocUploadStrategyFactory;
    @Mock
    private TempSourceDocumentRepository tempSourceDocumentRepository;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private DocumentRetentionConfigService retentionConfigService;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService(adminDocUploadStrategyFactory, tempSourceDocumentRepository, vectorStore, tempSourceDocumentRepository, eventPublisher, retentionConfigService);
    }

    @Test
    @DisplayName("download returns doc when found")
    void download_returnsDoc_whenFound() {
        TempSourceDocumentEntity entity = new TempSourceDocumentEntity();
        entity.setFileName("test.pdf");
        entity.setContent("hello".getBytes());

        when(tempSourceDocumentRepository.findByFileName("test.pdf")).thenReturn(Optional.of(entity));

        var result = uploadService.download("test.pdf");

        assertEquals("test.pdf", result.filename());
        assertNotNull(result.content());
    }

    @Test
    @DisplayName("download throws when not found")
    void download_throwsWhenNotFound() {
        when(tempSourceDocumentRepository.findByFileName("missing.pdf")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> uploadService.download("missing.pdf"));
    }

    @Test
    @DisplayName("uploadPersonalDocument saves and triggers async processing")
    void uploadPersonalDocument_savesAndTriggersAsync() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("doc.pdf");
        when(file.getBytes()).thenReturn("content".getBytes());

        DocumentToUpload document = mock(DocumentToUpload.class);
        when(document.file()).thenReturn(file);

        TempSourceDocumentEntity saved = new TempSourceDocumentEntity();
        saved.setId(5L);
        when(tempSourceDocumentRepository.save(any())).thenReturn(saved);

        uploadService.uploadPersonalDocument(document, "user-uuid");

        verify(eventPublisher).publishEvent(new PersonalDocumentUploadedEvent(5L));
    }

    @Test
    @DisplayName("getUserPersonalDocs returns mapped list with availability status and TTL")
    void getUserPersonalDocs_returnsMappedList() {
        TempSourceDocumentEntity doc = new TempSourceDocumentEntity();
        doc.setFileName("file.pdf");
        doc.setUploadedAt(LocalDateTime.now());
        doc.setStatus(EmbeddingStatus.PROCESSED);
        doc.setAvailabilityStatus(AvailabilityStatus.ACTIVE);

        when(tempSourceDocumentRepository.findAllByUserUuid("uuid")).thenReturn(List.of(doc));
        when(retentionConfigService.get()).thenReturn(new DocumentRetentionConfig(30, 30));

        List<PersonalDoc> result = uploadService.getUserPersonalDocs("uuid");

        assertEquals(1, result.size());
        assertEquals("file.pdf", result.get(0).title());
        assertEquals(AvailabilityStatus.ACTIVE, result.get(0).availabilityStatus());
        // Actif : uploadé maintenant -> ~30 jours restants avant archivage.
        assertNotNull(result.get(0).timeToLiveInDays());
        assertTrue(result.get(0).timeToLiveInDays() <= 30 && result.get(0).timeToLiveInDays() >= 28);
    }

    @Test
    @DisplayName("getUserPersonalDocs returns days before deletion for archived docs")
    void getUserPersonalDocs_returnsDaysBeforeDeletion_forArchivedDocs() {
        TempSourceDocumentEntity doc = new TempSourceDocumentEntity();
        doc.setFileName("archived.pdf");
        doc.setUploadedAt(LocalDateTime.now().minusDays(40));
        doc.setStatus(EmbeddingStatus.PROCESSED);
        doc.setAvailabilityStatus(AvailabilityStatus.ARCHIVED);
        doc.setArchivedAt(LocalDateTime.now().minusDays(5));

        when(tempSourceDocumentRepository.findAllByUserUuid("uuid")).thenReturn(List.of(doc));
        when(retentionConfigService.get()).thenReturn(new DocumentRetentionConfig(30, 30));

        List<PersonalDoc> result = uploadService.getUserPersonalDocs("uuid");

        assertEquals(AvailabilityStatus.ARCHIVED, result.get(0).availabilityStatus());
        // Archivé depuis 5 jours -> ~25 jours restants avant suppression (30 - 5).
        assertNotNull(result.get(0).timeToLiveInDays());
        assertTrue(result.get(0).timeToLiveInDays() <= 25 && result.get(0).timeToLiveInDays() >= 23);
    }

    @Test
    @DisplayName("reactivatePersonalDocument reactivates an archived document")
    void reactivatePersonalDocument_reactivatesArchivedDocument() {
        TempSourceDocumentEntity doc = new TempSourceDocumentEntity();
        doc.setFileName("file.pdf");
        doc.setUserUuid("uuid");
        doc.setAvailabilityStatus(AvailabilityStatus.ARCHIVED);
        doc.setArchivedAt(LocalDateTime.now().minusDays(5));
        doc.setUploadedAt(LocalDateTime.now().minusDays(40));

        when(tempSourceDocumentRepository.findByFileNameAndUserUuid("file.pdf", "uuid"))
                .thenReturn(Optional.of(doc));

        uploadService.reactivatePersonalDocument("file.pdf", "uuid");

        assertEquals(AvailabilityStatus.ACTIVE, doc.getAvailabilityStatus());
        assertNull(doc.getArchivedAt());
        assertTrue(doc.getUploadedAt().isAfter(LocalDateTime.now().minusMinutes(1)));
        verify(tempSourceDocumentRepository).save(doc);
    }

    @Test
    @DisplayName("reactivatePersonalDocument throws when already active")
    void reactivatePersonalDocument_throwsWhenAlreadyActive() {
        TempSourceDocumentEntity doc = new TempSourceDocumentEntity();
        doc.setFileName("file.pdf");
        doc.setAvailabilityStatus(AvailabilityStatus.ACTIVE);

        when(tempSourceDocumentRepository.findByFileNameAndUserUuid("file.pdf", "uuid"))
                .thenReturn(Optional.of(doc));

        assertThrows(IllegalStateException.class,
                () -> uploadService.reactivatePersonalDocument("file.pdf", "uuid"));
        verify(tempSourceDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("reactivatePersonalDocument throws when not found")
    void reactivatePersonalDocument_throwsWhenNotFound() {
        when(tempSourceDocumentRepository.findByFileNameAndUserUuid("missing.pdf", "uuid"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> uploadService.reactivatePersonalDocument("missing.pdf", "uuid"));
    }
}
