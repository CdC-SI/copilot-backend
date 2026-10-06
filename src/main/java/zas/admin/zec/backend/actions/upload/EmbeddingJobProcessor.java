package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import zas.admin.zec.backend.actions.upload.model.EmbeddingChunkResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingJobResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.actions.upload.strategy.EmbeddedDocUploadStrategy;
import zas.admin.zec.backend.config.properties.EmbeddingServiceProperties;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Machine à états du traitement d'embedding d'un document personnel via l'API asynchrone
 * du service externe ({@code POST /jobs} → polling {@code GET /jobs/{id}} → {@code GET /jobs/{id}/result}).
 * <p>
 * L'état est persisté dans {@code temp_source_document} ({@code job_id}, {@code job_attempts},
 * {@code job_submitted_at}, {@code next_poll_at}) afin de survivre aux redémarrages du backend.
 * Un document est d'abord <em>réservé</em> ({@link #claimDue()} / {@link #claim(Long)}) via un
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} qui pose un bail sur {@code next_poll_at} : une seule
 * instance du backend le traite à la fois. Les appels HTTP sont toujours effectués hors transaction.
 */
@Slf4j
@Service
public class EmbeddingJobProcessor {

    private final EmbeddingServiceClient embeddingServiceClient;
    private final DocumentRepository documentRepository;
    private final TempSourceDocumentRepository tempSourceDocumentRepository;
    private final TransactionTemplate transactionTemplate;
    private final EmbeddingServiceProperties properties;

    public EmbeddingJobProcessor(EmbeddingServiceClient embeddingServiceClient,
                                 DocumentRepository documentRepository,
                                 TempSourceDocumentRepository tempSourceDocumentRepository,
                                 TransactionTemplate transactionTemplate,
                                 EmbeddingServiceProperties properties) {

        this.embeddingServiceClient = embeddingServiceClient;
        this.documentRepository = documentRepository;
        this.tempSourceDocumentRepository = tempSourceDocumentRepository;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /**
     * Réserve un lot de documents en attente dont l'échéance est atteinte.
     */
    public List<Long> claimDue() {
        return transactionTemplate.execute(status -> {
            var now = LocalDateTime.now();
            var ids = tempSourceDocumentRepository.lockDueForEmbedding(now, properties.batchSize());
            if (!ids.isEmpty()) {
                tempSourceDocumentRepository.leaseUntil(ids, now.plus(properties.lease()));
            }
            return ids;
        });
    }

    /**
     * Réserve un document précis (traitement immédiat après upload).
     *
     * @return {@code false} si le document est déjà réservé, n'est plus en attente ou pas encore échu
     */
    public boolean claim(Long tempDocId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            var now = LocalDateTime.now();
            return tempSourceDocumentRepository.lockIfDueForEmbedding(tempDocId, now)
                    .map(id -> tempSourceDocumentRepository.leaseUntil(List.of(id), now.plus(properties.lease())) > 0)
                    .orElse(false);
        }));
    }

    /**
     * Fait avancer d'une étape le traitement d'un document préalablement réservé.
     * Ne lève jamais d'exception.
     */
    public void process(Long tempDocId) {
        JobSnapshot job = null;
        try {
            job = transactionTemplate.execute(status -> tempSourceDocumentRepository.findById(tempDocId)
                    .filter(doc -> doc.getStatus() == EmbeddingStatus.PENDING)
                    .map(JobSnapshot::of)
                    .orElse(null));

            if (job == null) {
                return;
            }
            if (isTimedOut(job)) {
                if (job.jobId() != null) {
                    embeddingServiceClient.cancelQuietly(job.jobId(), job.userUuid());
                }
                fail(job, "timeout de " + properties.jobTimeout() + " dépassé");
                return;
            }

            if (job.jobId() == null) {
                submit(job);
            } else {
                poll(job);
            }
        } catch (Exception e) {
            log.error("Erreur inattendue lors du traitement d'embedding du document ID: {}", tempDocId, e);
            reschedule(tempDocId, job == null ? null : job.jobId(), properties.retryDelay());
        }
    }

    /**
     * Libère immédiatement un document réservé (ex. : tâche refusée par l'executor).
     */
    public void release(Long tempDocId) {
        reschedule(tempDocId, Duration.ZERO);
    }

    private void submit(JobSnapshot job) {
        if (job.attempts() >= properties.maxSubmitAttempts()) {
            fail(job, "nombre maximal de soumissions (" + properties.maxSubmitAttempts() + ") atteint");
            return;
        }

        var content = transactionTemplate.execute(status -> tempSourceDocumentRepository.findById(job.id())
                .map(TempSourceDocumentEntity::getContent)
                .orElse(null));
        if (content == null) {
            return;
        }

        EmbeddingJobResponse response;
        try {
            response = embeddingServiceClient.submit(content, job.userUuid(), job.fileName());
        } catch (EmbeddingServiceException e) {
            if (e.isPermanent()) {
                fail(job, "soumission refusée : " + e.getMessage());
            } else {
                log.warn("Soumission du document ID: {} en échec transitoire, nouvel essai dans {} : {}",
                        job.id(), properties.retryDelay(), e.getMessage());
                updateIfPending(job.id(), job.jobId(), doc -> {
                    if (doc.getJobSubmittedAt() == null) {
                        doc.setJobSubmittedAt(LocalDateTime.now());
                    }
                    doc.setNextPollAt(LocalDateTime.now().plus(properties.retryDelay()));
                });
            }
            return;
        }

        boolean recorded = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                tempSourceDocumentRepository.findByIdForUpdate(job.id())
                        .filter(doc -> doc.getStatus() == EmbeddingStatus.PENDING && doc.getJobId() == null)
                        .map(doc -> {
                            var now = LocalDateTime.now();
                            doc.setJobId(response.jobId());
                            doc.setJobSubmittedAt(now);
                            doc.setJobAttempts(doc.getJobAttempts() + 1);
                            doc.setNextPollAt(now.plus(properties.pollInterval()));
                            return true;
                        })
                        .orElse(false)));

        if (recorded) {
            log.info("Document ID: {} soumis au service d'embedding (job {}, {} page(s))",
                    job.id(), response.jobId(), response.pagesTotal());
        } else {
            log.info("Document ID: {} supprimé ou déjà soumis entre-temps, annulation du job {}", job.id(), response.jobId());
            embeddingServiceClient.cancelQuietly(response.jobId(), job.userUuid());
        }
    }

    private void poll(JobSnapshot job) {
        try {
            var response = embeddingServiceClient.status(job.jobId(), job.userUuid());
            switch (response.jobStatus()) {
                case QUEUED, RUNNING, UNKNOWN -> reschedule(job, properties.pollInterval());
                case COMPLETED, COMPLETED_WITH_ERRORS -> {
                    if (response.pagesFailed() != null && response.pagesFailed() > 0) {
                        log.warn("Job {} (document ID: {}) terminé avec {} page(s) en échec sur {}",
                                job.jobId(), job.id(), response.pagesFailed(), response.pagesTotal());
                    }
                    persist(job, embeddingServiceClient.result(job.jobId(), job.userUuid()));
                }
                case FAILED, CANCELLED -> fail(job, "job " + response.status() + " : " + response.error());
            }
        } catch (EmbeddingServiceException e) {
            if (e.isNotReady()) {
                reschedule(job, properties.pollInterval());
            } else if (e.isJobGone()) {
                resetForResubmission(job, e);
            } else if (e.isPermanent()) {
                fail(job, e.getMessage());
            } else {
                log.warn("Polling du job {} (document ID: {}) en échec transitoire : {}",
                        job.jobId(), job.id(), e.getMessage());
                reschedule(job, properties.retryDelay());
            }
        }
    }

    private void persist(JobSnapshot job, List<EmbeddingChunkResponse> chunks) {
        if (chunks.isEmpty()) {
            fail(job, "aucun chunk retourné par le service");
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            var doc = tempSourceDocumentRepository.findByIdForUpdate(job.id())
                    .filter(d -> d.getStatus() == EmbeddingStatus.PENDING && Objects.equals(d.getJobId(), job.jobId()));
            if (doc.isEmpty()) {
                log.info("Document ID: {} supprimé ou déjà traité, résultat du job {} ignoré", job.id(), job.jobId());
                return;
            }

            documentRepository.saveAll(chunks.stream().map(this::toDocumentEntity).toList());
            doc.get().setStatus(EmbeddingStatus.PROCESSED);
            doc.get().setNextPollAt(null);

            log.info("Embedding terminé avec succès pour le document ID: {} ({} chunks persistés)",
                    job.id(), chunks.size());
        });
    }

    private void resetForResubmission(JobSnapshot job, EmbeddingServiceException e) {
        log.warn("Job {} (document ID: {}) perdu côté service ({}), resoumission",
                job.jobId(), job.id(), e.getCode() != null ? e.getCode() : "HTTP " + e.getHttpStatus());

        updateIfPending(job.id(), job.jobId(), doc -> {
            if (!Objects.equals(doc.getJobId(), job.jobId())) {
                return;
            }
            doc.setJobId(null);
            doc.setJobSubmittedAt(null);
            doc.setNextPollAt(LocalDateTime.now());
        });
    }

    private void fail(JobSnapshot job, String reason) {
        log.error("Échec de l'embedding du document ID: {} ({}) : {}", job.id(), job.fileName(), reason);
        try {
            updateIfPending(job.id(), job.jobId(), doc -> {
                doc.setStatus(EmbeddingStatus.FAILED);
                doc.setNextPollAt(null);
            });
        } catch (Exception ex) {
            log.error("Impossible de passer le document ID: {} en {}", job.id(), EmbeddingStatus.FAILED, ex);
        }
    }

    private void reschedule(Long tempDocId, Duration delay) {
        reschedule(tempDocId, null, delay);
    }

    private void reschedule(JobSnapshot job, Duration delay) {
        reschedule(job.id(), job.jobId(), delay);
    }

    private void reschedule(Long tempDocId, String expectedJobId, Duration delay) {
        try {
            updateIfPending(tempDocId, expectedJobId,
                    doc -> doc.setNextPollAt(LocalDateTime.now().plus(delay)));
        } catch (Exception ex) {
            log.error("Impossible de replanifier le document ID: {}", tempDocId, ex);
        }
    }

    private void updateIfPending(Long tempDocId, String expectedJobId,
                                 Consumer<TempSourceDocumentEntity> update) {
        transactionTemplate.executeWithoutResult(status -> tempSourceDocumentRepository.findByIdForUpdate(tempDocId)
                .filter(doc -> doc.getStatus() == EmbeddingStatus.PENDING)
                .filter(doc -> Objects.equals(doc.getJobId(), expectedJobId))
                .ifPresent(update));
    }

    private boolean isTimedOut(JobSnapshot job) {
        return job.submittedAt() != null
                && LocalDateTime.now().isAfter(job.submittedAt().plus(properties.jobTimeout()));
    }

    private DocumentEntity toDocumentEntity(EmbeddingChunkResponse chunk) {
        var entity = new DocumentEntity();
        entity.setContent(chunk.content());
        entity.setEmbedding(EmbeddedDocUploadStrategy.parseEmbedding(chunk.embedding()));
        entity.setMetadata(chunk.metadata());
        return entity;
    }

    private record JobSnapshot(Long id, String jobId, String userUuid, String fileName,
                               int attempts, LocalDateTime submittedAt) {

        static JobSnapshot of(TempSourceDocumentEntity doc) {
            return new JobSnapshot(doc.getId(), doc.getJobId(), doc.getUserUuid(), doc.getFileName(),
                    doc.getJobAttempts(), doc.getJobSubmittedAt());
        }
    }
}
