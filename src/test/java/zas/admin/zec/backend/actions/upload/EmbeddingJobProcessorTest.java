package zas.admin.zec.backend.actions.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import zas.admin.zec.backend.actions.upload.model.EmbeddingChunkResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingJobResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.config.properties.EmbeddingServiceProperties;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmbeddingJobProcessorTest {

    private static final long DOC_ID = 1L;
    private static final String USER = "user-uuid";
    private static final String FILE = "doc.pdf";
    private static final byte[] PDF = "%PDF".getBytes();

    @Mock
    private EmbeddingServiceClient client;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private TempSourceDocumentRepository tempRepo;

    private EmbeddingJobProcessor processor;
    private TempSourceDocumentEntity doc;

    @BeforeEach
    void setUp() {
        var properties = new EmbeddingServiceProperties("http://service", Duration.ofSeconds(3),
                Duration.ofSeconds(30), Duration.ofMinutes(5), 10, 3, Duration.ofHours(6),
                Duration.ofSeconds(60), DataSize.ofMegabytes(64));
        var transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        processor = new EmbeddingJobProcessor(client, documentRepository, tempRepo, transactionTemplate, properties);

        doc = new TempSourceDocumentEntity();
        doc.setId(DOC_ID);
        doc.setUserUuid(USER);
        doc.setFileName(FILE);
        doc.setContent(PDF);
        doc.setStatus(EmbeddingStatus.PENDING);
        when(tempRepo.findById(DOC_ID)).thenAnswer(inv -> Optional.of(doc));
        when(tempRepo.findByIdForUpdate(DOC_ID)).thenAnswer(inv -> Optional.of(doc));
    }

    private void givenSubmittedJob(String jobId) {
        doc.setJobId(jobId);
        doc.setJobAttempts(1);
        doc.setJobSubmittedAt(LocalDateTime.now().minusMinutes(1));
    }

    private static EmbeddingJobResponse job(String jobId, String status) {
        return new EmbeddingJobResponse(jobId, status, 2, 1, 0, null);
    }

    @Test
    @DisplayName("submits the raw PDF and stores the job id")
    void process_submitsDocument() {
        when(client.submit(PDF, USER, FILE)).thenReturn(job("job-1", "queued"));

        processor.process(DOC_ID);

        assertEquals("job-1", doc.getJobId());
        assertEquals(1, doc.getJobAttempts());
        assertNotNull(doc.getJobSubmittedAt());
        assertTrue(doc.getNextPollAt().isAfter(LocalDateTime.now()));
        assertEquals(EmbeddingStatus.PENDING, doc.getStatus());
    }

    @Test
    @DisplayName("marks document FAILED when submission is permanently rejected (413)")
    void process_failsOnPermanentSubmitError() {
        when(client.submit(any(), any(), any()))
                .thenThrow(new EmbeddingServiceException(413, "too_large", "too large"));

        processor.process(DOC_ID);

        assertEquals(EmbeddingStatus.FAILED, doc.getStatus());
        assertNull(doc.getJobId());
    }

    @Test
    @DisplayName("keeps document PENDING and retries later on transient submit error")
    void process_retriesOnTransientSubmitError() {
        when(client.submit(any(), any(), any()))
                .thenThrow(new EmbeddingServiceException(503, null, "unavailable"));

        processor.process(DOC_ID);

        assertEquals(EmbeddingStatus.PENDING, doc.getStatus());
        assertNull(doc.getJobId());
        assertNotNull(doc.getJobSubmittedAt());
        assertTrue(doc.getNextPollAt().isAfter(LocalDateTime.now().plusSeconds(20)));
    }

    @Test
    @DisplayName("marks document FAILED once max submit attempts is reached")
    void process_failsWhenMaxAttemptsReached() {
        doc.setJobAttempts(3);

        processor.process(DOC_ID);

        assertEquals(EmbeddingStatus.FAILED, doc.getStatus());
        verify(client, never()).submit(any(), any(), any());
    }

    @Test
    @DisplayName("reschedules while the job is running")
    void process_reschedulesWhileRunning() {
        givenSubmittedJob("job-1");
        when(client.status("job-1", USER)).thenReturn(job("job-1", "running"));

        processor.process(DOC_ID);

        assertEquals(EmbeddingStatus.PENDING, doc.getStatus());
        assertTrue(doc.getNextPollAt().isAfter(LocalDateTime.now()));
        verify(client, never()).result(any(), any());
    }

    @Test
    @DisplayName("persists chunks and marks PROCESSED once the job is completed")
    void process_persistsResultWhenCompleted() {
        givenSubmittedJob("job-1");
        when(client.status("job-1", USER)).thenReturn(job("job-1", "completed_with_errors"));
        when(client.result("job-1", USER)).thenReturn(List.of(
                new EmbeddingChunkResponse("chunk", "0.1,0.2", Map.of("title", FILE, "user_uuid", USER))));

        processor.process(DOC_ID);

        verify(documentRepository).saveAll(argThat(docs -> docs.iterator().hasNext()));
        assertEquals(EmbeddingStatus.PROCESSED, doc.getStatus());
        assertNull(doc.getNextPollAt());
    }

    @Test
    @DisplayName("ignores the result when the document was resubmitted meanwhile")
    void process_ignoresStaleResult() {
        givenSubmittedJob("job-1");
        when(client.status("job-1", USER)).thenReturn(job("job-1", "completed"));
        when(client.result("job-1", USER)).thenAnswer(inv -> {
            doc.setJobId("job-2");
            return List.of(new EmbeddingChunkResponse("chunk", "0.1", Map.of()));
        });

        processor.process(DOC_ID);

        verify(documentRepository, never()).saveAll(any());
        assertEquals(EmbeddingStatus.PENDING, doc.getStatus());
    }

    @Test
    @DisplayName("marks document FAILED when the job failed")
    void process_failsWhenJobFailed() {
        givenSubmittedJob("job-1");
        when(client.status("job-1", USER)).thenReturn(job("job-1", "failed"));

        processor.process(DOC_ID);

        assertEquals(EmbeddingStatus.FAILED, doc.getStatus());
    }

    @Test
    @DisplayName("resets the job for resubmission when the job is lost (410)")
    void process_resubmitsWhenJobLost() {
        givenSubmittedJob("job-1");
        when(client.status("job-1", USER))
                .thenThrow(new EmbeddingServiceException(410, EmbeddingServiceException.CODE_JOB_LOST, "gone"));

        processor.process(DOC_ID);

        assertNull(doc.getJobId());
        assertNull(doc.getJobSubmittedAt());
        assertEquals(EmbeddingStatus.PENDING, doc.getStatus());
        assertFalse(doc.getNextPollAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("keeps polling when the result is not ready yet (409)")
    void process_keepsPollingOnConflict() {
        givenSubmittedJob("job-1");
        when(client.status("job-1", USER)).thenReturn(job("job-1", "completed"));
        when(client.result("job-1", USER)).thenThrow(new EmbeddingServiceException(409, null, "not ready"));

        processor.process(DOC_ID);

        assertEquals("job-1", doc.getJobId());
        assertEquals(EmbeddingStatus.PENDING, doc.getStatus());
    }

    @Test
    @DisplayName("cancels the job and marks FAILED when the job timed out")
    void process_failsOnTimeout() {
        givenSubmittedJob("job-1");
        doc.setJobSubmittedAt(LocalDateTime.now().minusHours(7));

        processor.process(DOC_ID);

        verify(client).cancelQuietly("job-1", USER);
        verify(client, never()).status(any(), any());
        assertEquals(EmbeddingStatus.FAILED, doc.getStatus());
    }

    @Test
    @DisplayName("does nothing when the document is no longer pending")
    void process_skipsNonPendingDocument() {
        doc.setStatus(EmbeddingStatus.PROCESSED);

        processor.process(DOC_ID);

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("claimDue leases the locked documents")
    void claimDue_leasesLockedDocuments() {
        when(tempRepo.lockDueForEmbedding(any(), eq(10))).thenReturn(List.of(1L, 2L));

        var ids = processor.claimDue();

        assertEquals(List.of(1L, 2L), ids);
        verify(tempRepo).leaseUntil(eq(List.of(1L, 2L)), argThat(until -> until.isAfter(LocalDateTime.now().plusMinutes(4))));
    }
}
