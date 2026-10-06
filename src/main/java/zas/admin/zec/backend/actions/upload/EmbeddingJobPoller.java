package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Poller périodique des documents personnels en attente d'embedding : réserve un lot de documents
 * échus et délègue chaque étape (soumission, polling, récupération du résultat) à
 * {@link EmbeddingJobProcessor} sur l'executor asynchrone.
 */
@Slf4j
@Component
public class EmbeddingJobPoller {

    private final EmbeddingJobProcessor processor;
    private final TaskExecutor asyncExecutor;

    public EmbeddingJobPoller(EmbeddingJobProcessor processor,
                              @Qualifier("asyncExecutor") TaskExecutor asyncExecutor) {
        this.processor = processor;
        this.asyncExecutor = asyncExecutor;
    }

    @Scheduled(fixedDelayString = "${embedding-service.poll-interval:3s}")
    public void pollPendingDocuments() {
        try {
            for (Long id : processor.claimDue()) {
                dispatch(id);
            }
        } catch (Exception e) {
            log.error("Erreur lors de la réservation des documents en attente d'embedding", e);
        }
    }

    private void dispatch(Long id) {
        try {
            asyncExecutor.execute(() -> processor.process(id));
        } catch (TaskRejectedException e) {
            log.warn("Executor saturé, document ID: {} replanifié", id);
            processor.release(id);
        }
    }
}
