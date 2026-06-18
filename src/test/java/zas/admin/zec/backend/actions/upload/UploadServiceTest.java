package zas.admin.zec.backend.actions.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.actions.upload.model.PersonalDoc;
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
    private UploadAsyncProcessor uploadAsyncProcessor;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService(adminDocUploadStrategyFactory, tempSourceDocumentRepository, vectorStore, tempSourceDocumentRepository, uploadAsyncProcessor);
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

        verify(uploadAsyncProcessor).processEmbedding(5L);
    }

    @Test
    @DisplayName("getUserPersonalDocs returns mapped list")
    void getUserPersonalDocs_returnsMappedList() {
        TempSourceDocumentEntity doc = new TempSourceDocumentEntity();
        doc.setFileName("file.pdf");
        doc.setUploadedAt(LocalDateTime.now());
        doc.setStatus(EmbeddingStatus.PROCESSED);

        when(tempSourceDocumentRepository.findAllByUserUuid("uuid")).thenReturn(List.of(doc));

        List<PersonalDoc> result = uploadService.getUserPersonalDocs("uuid");

        assertEquals(1, result.size());
        assertEquals("file.pdf", result.get(0).title());
    }
}
