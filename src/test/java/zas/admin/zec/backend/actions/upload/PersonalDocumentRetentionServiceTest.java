package zas.admin.zec.backend.actions.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import zas.admin.zec.backend.actions.upload.model.AvailabilityStatus;
import zas.admin.zec.backend.actions.upload.model.DocumentRetentionConfig;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalDocumentRetentionServiceTest {

    @Mock
    private TempSourceDocumentRepository tempSourceDocumentRepository;
    @Mock
    private DocumentRetentionConfigService retentionConfigService;
    @Mock
    private VectorStore vectorStore;

    @Test
    @DisplayName("archiveExpired archives active expired documents")
    void archiveExpired_archivesActiveExpiredDocuments() {
        var service = new PersonalDocumentRetentionService(
                tempSourceDocumentRepository, retentionConfigService, vectorStore);

        var doc = new TempSourceDocumentEntity();
        doc.setFileName("old.pdf");
        doc.setUserUuid("uuid");
        doc.setAvailabilityStatus(AvailabilityStatus.ACTIVE);

        when(tempSourceDocumentRepository
                .findAllByAvailabilityStatusAndUserUuidNotNullAndUploadedAtBefore(eq(AvailabilityStatus.ACTIVE), any()))
                .thenReturn(List.of(doc));

        service.archiveExpired(new DocumentRetentionConfig(30, 30));

        assertEquals(AvailabilityStatus.ARCHIVED, doc.getAvailabilityStatus());
        assertNotNull(doc.getArchivedAt());
        verify(tempSourceDocumentRepository).saveAll(List.of(doc));
    }

    @Test
    @DisplayName("deleteExpired deletes archived expired documents from db and vector store")
    void deleteExpired_deletesArchivedExpiredDocuments() {
        var service = new PersonalDocumentRetentionService(
                tempSourceDocumentRepository, retentionConfigService, vectorStore);

        var doc = new TempSourceDocumentEntity();
        doc.setFileName("archived.pdf");
        doc.setUserUuid("uuid");
        doc.setAvailabilityStatus(AvailabilityStatus.ARCHIVED);
        doc.setArchivedAt(LocalDateTime.now().minusDays(40));

        when(tempSourceDocumentRepository
                .findAllByAvailabilityStatusAndUserUuidNotNullAndArchivedAtBefore(eq(AvailabilityStatus.ARCHIVED), any()))
                .thenReturn(List.of(doc));

        service.deleteExpired(new DocumentRetentionConfig(30, 30));

        ArgumentCaptor<Filter.Expression> filterCaptor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(filterCaptor.capture());
        assertNotNull(filterCaptor.getValue());
        verify(tempSourceDocumentRepository).delete(doc);
    }

    @Test
    @DisplayName("runDailyLifecycle runs archival then deletion")
    void runDailyLifecycle_runsArchivalThenDeletion() {
        var service = new PersonalDocumentRetentionService(
                tempSourceDocumentRepository, retentionConfigService, vectorStore);

        when(retentionConfigService.get()).thenReturn(new DocumentRetentionConfig(30, 30));
        when(tempSourceDocumentRepository
                .findAllByAvailabilityStatusAndUserUuidNotNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of());
        when(tempSourceDocumentRepository
                .findAllByAvailabilityStatusAndUserUuidNotNullAndArchivedAtBefore(any(), any()))
                .thenReturn(List.of());

        service.runDailyLifecycle();

        verify(retentionConfigService).get();
        verify(tempSourceDocumentRepository)
                .findAllByAvailabilityStatusAndUserUuidNotNullAndUploadedAtBefore(eq(AvailabilityStatus.ACTIVE), any());
        verify(tempSourceDocumentRepository)
                .findAllByAvailabilityStatusAndUserUuidNotNullAndArchivedAtBefore(eq(AvailabilityStatus.ARCHIVED), any());
    }
}
