package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Machine à états de l'extraction du texte d'une pièce jointe PDF via l'API asynchrone du pdf-ocr-pipeline
 * ({@code POST /jobs} en {@code mode=extract} → polling {@code GET /jobs/{id}} → {@code GET /jobs/{id}/result}).
 * Le texte brut des pages est concaténé (séparateurs {@code === PAGE i / n ===}, comme l'OCR LLM) dans
 * {@code attachment.content} ; aucun résumé ni embedding n'est produit.
 * <p>
 * L'état est persisté dans {@code attachment} ({@code job_id}, {@code job_attempts}, {@code job_submitted_at},
 * {@code next_poll_at}) afin de survivre aux redémarrages du backend. Une pièce jointe est d'abord
 * <em>réservée</em> ({@link #claimDue()} / {@link #claim(Long)}) via un {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * qui pose un bail sur {@code next_poll_at} : une seule instance du backend la traite à la fois. Les appels HTTP
 * sont toujours effectués hors transaction, et les octets du fichier ne sont lus qu'à la soumission.
 */
@Slf4j
@Service
public class AttachmentJobProcessor {

    private final EmbeddingServiceClient pipelineClient;
    private final AttachmentRepository attachmentRepository;
    private final TransactionTemplate transactionTemplate;
    private final EmbeddingServiceProperties serviceProperties;
    private final AttachmentOcrProperties ocrProperties;

    public AttachmentJobProcessor(EmbeddingServiceClient pipelineClient,
                                  AttachmentRepository attachmentRepository,
                                  TransactionTemplate transactionTemplate,
                                  EmbeddingServiceProperties serviceProperties,
                                  AttachmentOcrProperties ocrProperties) {

        this.pipelineClient = pipelineClient;
        this.attachmentRepository = attachmentRepository;
        this.transactionTemplate = transactionTemplate;
        this.serviceProperties = serviceProperties;
        this.ocrProperties = ocrProperties;
    }

    /**
     * Réserve un lot de pièces jointes en attente dont l'échéance est atteinte.
     */
    public List<Long> claimDue() {
        return transactionTemplate.execute(status -> {
            var now = LocalDateTime.now();
            var ids = attachmentRepository.lockDueForPipeline(now, serviceProperties.batchSize());
            if (!ids.isEmpty()) {
                attachmentRepository.leaseUntil(ids, now.plus(serviceProperties.lease()));
            }
            return ids;
        });
    }

    /**
     * Réserve une pièce jointe précise (traitement immédiat après upload).
     *
     * @return {@code false} si la pièce jointe est déjà réservée, n'est plus en attente ou pas encore échue
     */
    public boolean claim(Long attachmentId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            var now = LocalDateTime.now();
            return attachmentRepository.lockIfDueForPipeline(attachmentId, now)
                    .map(id -> attachmentRepository.leaseUntil(List.of(id), now.plus(serviceProperties.lease())) > 0)
                    .orElse(false);
        }));
    }

    /**
     * Fait avancer d'une étape le traitement d'une pièce jointe préalablement réservée.
     * Ne lève jamais d'exception.
     */
    public void process(Long attachmentId) {
        JobSnapshot job = null;
        try {
            job = transactionTemplate.execute(status -> attachmentRepository.findPendingJob(attachmentId)
                    .map(JobSnapshot::of)
                    .orElse(null));

            if (job == null) {
                return;
            }
            if (isTimedOut(job)) {
                if (job.jobId() != null) {
                    pipelineClient.cancelQuietly(job.jobId(), job.userId());
                }
                fail(job, UploadErrorCode.EMBEDDING_TIMEOUT, "timeout de " + ocrProperties.jobTimeout() + " dépassé");
                return;
            }

            if (job.jobId() == null) {
                submit(job);
            } else {
                poll(job);
            }
        } catch (Exception e) {
            log.error("Erreur inattendue lors de l'extraction de la pièce jointe ID: {}", attachmentId, e);
            reschedule(attachmentId, job == null ? null : job.jobId(), serviceProperties.retryDelay());
        }
    }

    /**
     * Libère immédiatement une pièce jointe réservée (ex. : tâche refusée par l'executor).
     */
    public void release(Long attachmentId) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    attachmentRepository.leaseUntil(List.of(attachmentId), LocalDateTime.now()));
        } catch (Exception ex) {
            log.error("Impossible de libérer la pièce jointe ID: {}", attachmentId, ex);
        }
    }

    private void submit(JobSnapshot job) {
        if (job.attempts() >= serviceProperties.maxSubmitAttempts()) {
            fail(job, UploadErrorCode.OCR_FAILED,
                    "nombre maximal de soumissions (" + serviceProperties.maxSubmitAttempts() + ") atteint");
            return;
        }

        var pdf = transactionTemplate.execute(status -> attachmentRepository.findFileBytesById(job.id()).orElse(null));
        if (pdf == null) {
            return;
        }

        var response = trySubmit(job, pdf);
        if (response == null) {
            return;
        }

        boolean recorded = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            var now = LocalDateTime.now();
            return attachmentRepository.recordSubmission(job.id(), response.jobId(), now,
                    now.plus(ocrProperties.pollInterval())) > 0;
        }));

        if (recorded) {
            log.info("Pièce jointe ID: {} soumise au pdf-ocr-pipeline (job {}, {} page(s))",
                    job.id(), response.jobId(), response.pagesTotal());
        } else {
            log.info("Pièce jointe ID: {} supprimée ou déjà soumise entre-temps, annulation du job {}",
                    job.id(), response.jobId());
            pipelineClient.cancelQuietly(response.jobId(), job.userId());
        }
    }

    private EmbeddingJobResponse trySubmit(JobSnapshot job, byte[] pdf) {
        try {
            return pipelineClient.submit(pdf, job.userId(), job.filename(), EmbeddingServiceClient.MODE_EXTRACT);
        } catch (EmbeddingServiceException e) {
            if (e.isPermanent()) {
                fail(job, toErrorCode(e), "soumission refusée : " + e.getMessage());
            } else {
                log.warn("Soumission de la pièce jointe ID: {} en échec transitoire, nouvel essai dans {} : {}",
                        job.id(), serviceProperties.retryDelay(), e.getMessage());
                transactionTemplate.executeWithoutResult(status -> {
                    var now = LocalDateTime.now();
                    attachmentRepository.markSubmitRetry(job.id(), now, now.plus(serviceProperties.retryDelay()));
                });
            }
            return null;
        }
    }

    private void poll(JobSnapshot job) {
        try {
            var response = pipelineClient.status(job.jobId(), job.userId());
            switch (response.jobStatus()) {
                case QUEUED, RUNNING, UNKNOWN -> reschedule(job.id(), job.jobId(), ocrProperties.pollInterval());
                case COMPLETED, COMPLETED_WITH_ERRORS -> {
                    if (!response.pagesFailed().isEmpty()) {
                        log.warn("Job {} (pièce jointe ID: {}) terminé avec {} page(s) en échec sur {} : {}",
                                job.jobId(), job.id(), response.pagesFailed().size(), response.pagesTotal(),
                                response.pagesFailed());
                    }
                    persist(job, pipelineClient.result(job.jobId(), job.userId()));
                }
                case FAILED, CANCELLED -> fail(job, UploadErrorCode.OCR_FAILED, "job " + response.status() + " : "
                        + response.error() + (response.errorCode() != null ? " (" + response.errorCode() + ")" : ""));
            }
        } catch (EmbeddingServiceException e) {
            if (e.isNotReady()) {
                reschedule(job.id(), job.jobId(), ocrProperties.pollInterval());
            } else if (e.isJobGone()) {
                log.warn("Job {} (pièce jointe ID: {}) perdu côté pipeline ({}), resoumission",
                        job.jobId(), job.id(), e.getCode() != null ? e.getCode() : "HTTP " + e.getHttpStatus());
                transactionTemplate.executeWithoutResult(status ->
                        attachmentRepository.resetJob(job.id(), job.jobId(), LocalDateTime.now()));
            } else if (e.isPermanent()) {
                fail(job, UploadErrorCode.OCR_FAILED, e.getMessage());
            } else {
                log.warn("Polling du job {} (pièce jointe ID: {}) en échec transitoire : {}",
                        job.jobId(), job.id(), e.getMessage());
                reschedule(job.id(), job.jobId(), serviceProperties.retryDelay());
            }
        }
    }

    private void persist(JobSnapshot job, List<EmbeddingChunkResponse> pages) {
        var content = joinPages(pages);
        if (content == null) {
            fail(job, UploadErrorCode.EMBEDDING_NO_CONTENT, "aucun texte extrait (" + pages.size() + " page(s))");
            return;
        }

        Integer updated = transactionTemplate.execute(status ->
                attachmentRepository.markProcessed(job.id(), job.jobId(), content));
        if (updated != null && updated > 0) {
            log.info("Extraction terminée avec succès pour la pièce jointe ID: {} ({} page(s), {} caractères)",
                    job.id(), pages.size(), content.length());
        } else {
            log.info("Pièce jointe ID: {} supprimée ou déjà traitée, résultat du job {} ignoré", job.id(), job.jobId());
        }
    }

    /**
     * Concatène le texte brut des pages (une entrée par page, pages en échec incluses) au format de l'OCR LLM.
     *
     * @return le texte concaténé, ou {@code null} si aucune page ne contient de texte
     */
    static String joinPages(List<EmbeddingChunkResponse> pages) {
        var sorted = new ArrayList<>(pages);
        sorted.sort(Comparator.comparingInt(AttachmentJobProcessor::pageNum));

        int total = sorted.size();
        var sb = new StringBuilder(16_384);
        boolean hasText = false;
        for (int i = 0; i < total; i++) {
            var page = sorted.get(i);
            var text = page.content() == null ? "" : page.content().strip();
            sb.append("\n\n=== PAGE ").append(i + 1).append(" / ").append(total).append(" ===\n\n");
            if (isFailed(page)) {
                sb.append("[page ").append(i + 1).append(" : extraction du texte échouée]");
            } else {
                sb.append(text);
                hasText |= !text.isEmpty();
            }
        }
        return hasText ? sb.toString().strip() : null;
    }

    private static int pageNum(EmbeddingChunkResponse page) {
        return metadata(page).get("page_num") instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
    }

    private static boolean isFailed(EmbeddingChunkResponse page) {
        return Boolean.TRUE.equals(metadata(page).get("failed"));
    }

    private static Map<String, Object> metadata(EmbeddingChunkResponse page) {
        return page.metadata() == null ? Map.of() : page.metadata();
    }

    /**
     * Traduit un refus définitif du pipeline en code renvoyé au client lors du polling.
     */
    static UploadErrorCode toErrorCode(EmbeddingServiceException e) {
        if (e.getCode() == null) {
            return UploadErrorCode.OCR_FAILED;
        }
        return switch (e.getCode()) {
            case "too_many_pages" -> UploadErrorCode.TOO_MANY_PAGES;
            case "file_too_large" -> UploadErrorCode.FILE_TOO_LARGE;
            case "empty_file", "corrupt_pdf", "empty_pdf", "encrypted_pdf", "invalid_pdf" ->
                    UploadErrorCode.FILE_CONTENT_INVALID;
            default -> UploadErrorCode.OCR_FAILED;
        };
    }

    private void fail(JobSnapshot job, UploadErrorCode errorCode, String reason) {
        log.error("Échec de l'extraction de la pièce jointe ID: {} ({}) [{}] : {}",
                job.id(), job.filename(), errorCode, reason);
        try {
            transactionTemplate.executeWithoutResult(status ->
                    attachmentRepository.markFailed(job.id(), job.jobId(), errorCode));
        } catch (Exception ex) {
            log.error("Impossible de passer la pièce jointe ID: {} en {}", job.id(), AttachmentStatus.FAILED, ex);
        }
    }

    private void reschedule(Long attachmentId, String expectedJobId, Duration delay) {
        try {
            transactionTemplate.executeWithoutResult(status -> attachmentRepository.reschedulePending(
                    attachmentId, expectedJobId, LocalDateTime.now().plus(delay)));
        } catch (Exception ex) {
            log.error("Impossible de replanifier la pièce jointe ID: {}", attachmentId, ex);
        }
    }

    private boolean isTimedOut(JobSnapshot job) {
        return job.submittedAt() != null
                && LocalDateTime.now().isAfter(job.submittedAt().plus(ocrProperties.jobTimeout()));
    }

    private record JobSnapshot(Long id, String jobId, String userId, String filename,
                               int attempts, LocalDateTime submittedAt) {

        static JobSnapshot of(AttachmentJob job) {
            return new JobSnapshot(job.getId(), job.getJobId(), job.getUserId(), job.getFilename(),
                    job.getJobAttempts(), job.getJobSubmittedAt());
        }
    }
}
