package zas.admin.zec.backend.actions.converse;

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
import zas.admin.zec.backend.actions.upload.EmbeddingServiceClient;
import zas.admin.zec.backend.actions.upload.EmbeddingServiceException;
import zas.admin.zec.backend.actions.upload.model.EmbeddingChunkResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingJobResponse;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;
import zas.admin.zec.backend.config.properties.AttachmentOcrProperties;
import zas.admin.zec.backend.config.properties.EmbeddingServiceProperties;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository.AttachmentJob;

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
class AttachmentJobProcessorTest {

    private static final long ID = 1L;
    private static final String USER = "user-uuid";
    private static final String FILE = "doc.pdf";
    private static final byte[] PDF = "%PDF".getBytes();

    @Mock
    private EmbeddingServiceClient client;
    @Mock
    private AttachmentRepository repository;

    private AttachmentJobProcessor processor;

    @BeforeEach
    void setUp() {
        var serviceProperties = new EmbeddingServiceProperties("http://service", Duration.ofSeconds(3),
                Duration.ofSeconds(30), Duration.ofMinutes(5), 10, 3, Duration.ofHours(6),
                Duration.ofSeconds(60), DataSize.ofMegabytes(64));
        var ocrProperties = new AttachmentOcrProperties(AttachmentOcrProperties.Engine.PIPELINE, 1000,
                Duration.ofSeconds(5), Duration.ofHours(1));
        processor = new AttachmentJobProcessor(client, repository,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), serviceProperties, ocrProperties);

        when(repository.findFileBytesById(ID)).thenReturn(Optional.of(PDF));
        givenJob(null, 0, null);
    }

    private void givenJob(String jobId, int attempts, LocalDateTime submittedAt) {
        var job = mock(AttachmentJob.class);
        when(job.getId()).thenReturn(ID);
        when(job.getJobId()).thenReturn(jobId);
        when(job.getUserId()).thenReturn(USER);
        when(job.getFilename()).thenReturn(FILE);
        when(job.getJobAttempts()).thenReturn(attempts);
        when(job.getJobSubmittedAt()).thenReturn(submittedAt);
        when(repository.findPendingJob(ID)).thenReturn(Optional.of(job));
    }

    private void givenSubmittedJob() {
        givenJob("job-1", 1, LocalDateTime.now().minusMinutes(1));
    }

    private static EmbeddingJobResponse job(String status) {
        return new EmbeddingJobResponse("job-1", status, 2, 2, List.of(), null, null, null);
    }

    private static EmbeddingChunkResponse page(int num, String text, boolean failed) {
        return new EmbeddingChunkResponse(text, null, Map.of("page_num", num, "failed", failed));
    }

    @Test
    @DisplayName("submits the PDF in extract mode and records the job")
    void process_submitsInExtractMode() {
        when(client.submit(PDF, USER, FILE, EmbeddingServiceClient.MODE_EXTRACT)).thenReturn(job("queued"));
        when(repository.recordSubmission(eq(ID), eq("job-1"), any(), any())).thenReturn(1);

        processor.process(ID);

        verify(repository).recordSubmission(eq(ID), eq("job-1"), any(), any());
        verify(client, never()).cancelQuietly(any(), any());
    }

    @Test
    @DisplayName("cancels the job when the attachment was deleted during submission")
    void process_cancelsJobWhenAttachmentGone() {
        when(client.submit(any(), any(), any(), any())).thenReturn(job("queued"));
        when(repository.recordSubmission(any(), any(), any(), any())).thenReturn(0);

        processor.process(ID);

        verify(client).cancelQuietly("job-1", USER);
    }

    @Test
    @DisplayName("maps permanent pipeline rejections to explicit error codes")
    void process_mapsPermanentSubmitErrors() {
        when(client.submit(any(), any(), any(), any()))
                .thenThrow(new EmbeddingServiceException(413, "too_many_pages", "too many pages"));

        processor.process(ID);

        verify(repository).markFailed(ID, null, UploadErrorCode.TOO_MANY_PAGES);
    }

    @Test
    @DisplayName("retries later on transient submit error")
    void process_retriesOnTransientSubmitError() {
        when(client.submit(any(), any(), any(), any()))
                .thenThrow(new EmbeddingServiceException(503, null, "unavailable"));

        processor.process(ID);

        verify(repository).markSubmitRetry(eq(ID), any(), argThat(at -> at.isAfter(LocalDateTime.now().plusSeconds(20))));
        verify(repository, never()).markFailed(any(), any(), any());
    }

    @Test
    @DisplayName("fails after the maximum number of submissions")
    void process_failsAfterMaxAttempts() {
        givenJob(null, 3, LocalDateTime.now());

        processor.process(ID);

        verify(repository).markFailed(ID, null, UploadErrorCode.OCR_FAILED);
        verify(client, never()).submit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("reschedules while the job is running")
    void process_reschedulesRunningJob() {
        givenSubmittedJob();
        when(client.status("job-1", USER)).thenReturn(job("running"));

        processor.process(ID);

        verify(repository).reschedulePending(eq(ID), eq("job-1"), any());
        verify(client, never()).result(any(), any());
    }

    @Test
    @DisplayName("concatenates the raw page text on completion")
    void process_persistsConcatenatedText() {
        givenSubmittedJob();
        when(client.status("job-1", USER)).thenReturn(job("completed_with_errors"));
        when(client.result("job-1", USER)).thenReturn(List.of(
                page(2, "", true), page(1, " Hello ", false), page(3, "World", false)));
        when(repository.markProcessed(eq(ID), eq("job-1"), any())).thenReturn(1);

        processor.process(ID);

        verify(repository).markProcessed(ID, "job-1", """
                === PAGE 1 / 3 ===

                Hello

                === PAGE 2 / 3 ===

                [page 2 : extraction du texte échouée]

                === PAGE 3 / 3 ===

                World""");
    }

    @Test
    @DisplayName("fails with EMBEDDING_NO_CONTENT when no page has text")
    void process_failsWithoutText() {
        givenSubmittedJob();
        when(client.status("job-1", USER)).thenReturn(job("completed_with_errors"));
        when(client.result("job-1", USER)).thenReturn(List.of(page(1, " ", false), page(2, "", true)));

        processor.process(ID);

        verify(repository).markFailed(ID, "job-1", UploadErrorCode.EMBEDDING_NO_CONTENT);
        verify(repository, never()).markProcessed(any(), any(), any());
    }

    @Test
    @DisplayName("resets the job for resubmission when the pipeline lost it")
    void process_resubmitsLostJob() {
        givenSubmittedJob();
        when(client.status("job-1", USER)).thenThrow(new EmbeddingServiceException(410, "job_lost", "lost"));

        processor.process(ID);

        verify(repository).resetJob(eq(ID), eq("job-1"), any());
    }

    @Test
    @DisplayName("cancels and fails a job past the timeout")
    void process_timesOut() {
        givenJob("job-1", 1, LocalDateTime.now().minusHours(3));

        processor.process(ID);

        verify(client).cancelQuietly("job-1", USER);
        verify(repository).markFailed(ID, "job-1", UploadErrorCode.EMBEDDING_TIMEOUT);
    }

    @Test
    @DisplayName("fails when the pipeline job failed")
    void process_failsOnFailedJob() {
        givenSubmittedJob();
        when(client.status("job-1", USER)).thenReturn(job("failed"));

        processor.process(ID);

        verify(repository).markFailed(ID, "job-1", UploadErrorCode.OCR_FAILED);
    }

    @Test
    @DisplayName("does nothing when the attachment is no longer pending")
    void process_ignoresMissingAttachment() {
        when(repository.findPendingJob(ID)).thenReturn(Optional.empty());

        processor.process(ID);

        verifyNoInteractions(client);
    }
}
