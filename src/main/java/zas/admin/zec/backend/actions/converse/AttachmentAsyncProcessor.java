package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;

/**
 * Service dédié au traitement OCR asynchrone des pièces jointes.
 * Séparé de {@link ConversationService} pour permettre l'utilisation correcte du proxy Spring avec {@code @Async}.
 *
 * <p>Utilise {@link TransactionTemplate} (transactions programmatiques) plutôt que {@code @Transactional}
 * pour éviter les problèmes liés à la combinaison {@code @Async + @Transactional} :</p>
 * <ul>
 *   <li>La connexion DB n'est pas maintenue pendant l'appel OCR LLM (potentiellement long)</li>
 *   <li>Les mises à jour de statut FAILED sont commitées indépendamment du traitement principal</li>
 * </ul>
 *
 * <p><strong>Cycle de vie MultipartFile :</strong> ce processor ne reçoit jamais de {@code MultipartFile}.
 * Il travaille uniquement sur les bytes déjà persistés en base (via l'ID de l'entité), ce qui garantit
 * qu'il n'est pas affecté par la libération du stockage temporaire après le retour HTTP 202.</p>
 */
@Slf4j
@Service
public class AttachmentAsyncProcessor {

    private final AttachmentRepository attachmentRepository;
    private final LlmOcrService ocrService;
    private final TransactionTemplate transactionTemplate;

    public AttachmentAsyncProcessor(AttachmentRepository attachmentRepository,
                                    LlmOcrService ocrService,
                                    TransactionTemplate transactionTemplate) {

        this.attachmentRepository = attachmentRepository;
        this.ocrService = ocrService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Exécute l'OCR d'une pièce jointe de manière asynchrone.
     * <ol>
     *   <li>Transaction 1 : charge les bytes depuis la base (le MultipartFile a déjà été libéré)</li>
     *   <li>Hors transaction : appel OCR LLM (pas de connexion DB maintenue)</li>
     *   <li>Transaction 2 : persiste le contenu OCR et passe le statut à {@link AttachmentStatus#PROCESSED}</li>
     *   <li>En cas d'erreur : transaction indépendante pour passer le statut à {@link AttachmentStatus#FAILED}</li>
     * </ol>
     *
     * @param attachmentId l'ID de l'entité {@code AttachmentEntity} à traiter
     */
    @Async("asyncExecutor")
    public void processOcr(Long attachmentId) {
        log.info("Démarrage du traitement OCR asynchrone pour la pièce jointe ID: {}", attachmentId);

        try {
            // Transaction 1 : lecture des bytes depuis la base
            var fileBytes = transactionTemplate.execute(status -> {
                var entity = attachmentRepository.findById(attachmentId)
                        .orElseThrow(() -> new RuntimeException("Pièce jointe introuvable: " + attachmentId));
                return entity.getFileBytes();
            });

            // Hors transaction : appel OCR LLM (potentiellement long, pas de connexion DB maintenue)
            var content = ocrService.ocrFile(fileBytes);

            // Transaction 2 : persistance du contenu OCR + statut PROCESSED
            transactionTemplate.executeWithoutResult(status -> {
                var entity = attachmentRepository.findById(attachmentId)
                        .orElseThrow(() -> new RuntimeException("Pièce jointe introuvable: " + attachmentId));
                entity.setContent(content);
                entity.setStatus(AttachmentStatus.PROCESSED);
                attachmentRepository.save(entity);
            });

            log.info("OCR terminé avec succès pour la pièce jointe ID: {}", attachmentId);

        } catch (Exception e) {
            log.error("Erreur lors du traitement OCR pour la pièce jointe ID: {}", attachmentId, e);
            updateStatusSafely(attachmentId, AttachmentStatus.FAILED);
        }
    }

    /**
     * Met à jour le statut dans une transaction indépendante.
     * Ne propage pas d'exception pour ne pas masquer l'erreur originale.
     */
    private void updateStatusSafely(Long attachmentId, AttachmentStatus status) {
        try {
            transactionTemplate.executeWithoutResult(txStatus ->
                    attachmentRepository.findById(attachmentId).ifPresent(entity -> {
                        entity.setStatus(status);
                        attachmentRepository.save(entity);
                    })
            );
        } catch (Exception ex) {
            log.error("Impossible de mettre à jour le statut de la pièce jointe ID: {} vers {}", attachmentId, status, ex);
        }
    }
}

