package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import zas.admin.zec.backend.actions.upload.model.PersonalDocumentDeletedEvent;
import zas.admin.zec.backend.actions.upload.model.PersonalDocumentUploadedEvent;

/**
 * Réactions asynchrones (après commit) aux événements du cycle de vie des documents personnels.
 * Séparé de l'UploadService pour permettre l'utilisation correcte du proxy Spring avec @Async.
 * <p>
 * Le traitement d'embedding lui-même est piloté par {@link EmbeddingJobProcessor} : l'upload déclenche
 * uniquement une soumission immédiate, la suite (polling, récupération du résultat) étant assurée par
 * {@link EmbeddingJobPoller}.
 */
@Slf4j
@Service
public class UploadAsyncProcessor {

    private final EmbeddingJobProcessor embeddingJobProcessor;
    private final EmbeddingServiceClient embeddingServiceClient;

    public UploadAsyncProcessor(EmbeddingJobProcessor embeddingJobProcessor,
                                EmbeddingServiceClient embeddingServiceClient) {

        this.embeddingJobProcessor = embeddingJobProcessor;
        this.embeddingServiceClient = embeddingServiceClient;
    }

    /**
     * Soumet immédiatement le document au service d'embedding (sans attendre le prochain tick du poller).
     */
    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPersonalDocumentUploaded(PersonalDocumentUploadedEvent event) {
        log.info("Received PersonalDocumentUploadedEvent for tempDocId {}", event.tempDocId());
        if (embeddingJobProcessor.claim(event.tempDocId())) {
            embeddingJobProcessor.process(event.tempDocId());
        }
    }

    /**
     * Annule (best-effort) le job d'embedding d'un document supprimé avant la fin de son traitement.
     */
    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPersonalDocumentDeleted(PersonalDocumentDeletedEvent event) {
        embeddingServiceClient.cancelQuietly(event.jobId(), event.userUuid());
    }
}
