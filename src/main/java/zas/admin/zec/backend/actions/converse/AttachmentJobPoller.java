package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Poller périodique des pièces jointes PDF en attente d'extraction par le pdf-ocr-pipeline : réserve un lot de
 * pièces jointes échues et délègue chaque étape (soumission, polling, récupération du résultat) à
 * {@link AttachmentJobProcessor} sur l'executor des jobs du service.
 */
@Slf4j
@Component
public class AttachmentJobPoller {

    private final AttachmentJobProcessor processor;
    private final TaskExecutor executor;

    public AttachmentJobPoller(AttachmentJobProcessor processor,
                               @Qualifier("embeddingJobExecutor") TaskExecutor executor) {
        this.processor = processor;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${attachment-ocr.poll-interval:5s}")
    public void pollPendingAttachments() {
        try {
            for (Long id : processor.claimDue()) {
                dispatch(id);
            }
        } catch (Exception e) {
            log.error("Erreur lors de la réservation des pièces jointes en attente d'extraction", e);
        }
    }

    private void dispatch(Long id) {
        try {
            executor.execute(() -> processor.process(id));
        } catch (TaskRejectedException e) {
            log.warn("Executor saturé, pièce jointe ID: {} replanifiée", id);
            processor.release(id);
        }
    }
}
