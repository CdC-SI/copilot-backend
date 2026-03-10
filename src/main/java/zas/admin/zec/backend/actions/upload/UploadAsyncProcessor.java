package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import zas.admin.zec.backend.actions.upload.model.EmbeddingChunkResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingRequest;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.util.Base64;
import java.util.List;

/**
 * Service dédié au traitement asynchrone de l'upload de documents personnels.
 * Séparé de l'UploadService pour permettre l'utilisation correcte du proxy Spring avec @Async.
 * <p>
 * Utilise {@link TransactionTemplate} (transactions programmatiques) plutôt que {@code @Transactional}
 * pour éviter les problèmes liés à la combinaison {@code @Async + @Transactional} :
 * <ul>
 *   <li>La connexion DB n'est pas maintenue pendant l'appel au service externe</li>
 *   <li>Les mises à jour de statut (ERREUR) sont commitées indépendamment du traitement principal</li>
 *   <li>Meilleur contrôle du périmètre transactionnel</li>
 * </ul>
 */
@Slf4j
@Service
public class UploadAsyncProcessor {

    private final EmbeddingServiceClient embeddingServiceClient;
    private final DocumentRepository documentRepository;
    private final TempSourceDocumentRepository tempSourceDocumentRepository;
    private final TransactionTemplate transactionTemplate;

    public UploadAsyncProcessor(EmbeddingServiceClient embeddingServiceClient,
                                DocumentRepository documentRepository,
                                TempSourceDocumentRepository tempSourceDocumentRepository,
                                TransactionTemplate transactionTemplate) {

        this.embeddingServiceClient = embeddingServiceClient;
        this.documentRepository = documentRepository;
        this.tempSourceDocumentRepository = tempSourceDocumentRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Traite l'embedding d'un document personnel de manière asynchrone.
     * <ol>
     *   <li>Transaction 1 : charge le document depuis la base</li>
     *   <li>Hors transaction : appelle le service externe d'embedding (potentiellement long)</li>
     *   <li>Transaction 2 : persiste les chunks dans le vector store et met à jour le statut à TERMINEE</li>
     *   <li>En cas d'erreur : transaction indépendante pour passer le statut à ERREUR</li>
     * </ol>
     *
     * @param tempDocId l'ID du TempSourceDocumentEntity à traiter
     */
    @Async("asyncExecutor")
    public void processEmbedding(Long tempDocId) {
        log.info("Démarrage du traitement asynchrone d'embedding pour le document ID: {}", tempDocId);

        try {
            // Transaction 1 : lecture du document
            var docData = transactionTemplate.execute(status -> {
                var tempDoc = tempSourceDocumentRepository.findById(tempDocId)
                        .orElseThrow(() -> new RuntimeException("Document temporaire introuvable: " + tempDocId));
                return new DocumentData(
                        Base64.getEncoder().encodeToString(tempDoc.getContent()),
                        tempDoc.getUserUuid(),
                        tempDoc.getFileName()
                );
            });

            // Hors transaction : appel au service externe (pas de connexion DB maintenue)
            var request = new EmbeddingRequest(docData.dataUrl(), docData.userUuid(), docData.fileName());
            List<EmbeddingChunkResponse> chunks = embeddingServiceClient.embed(request);

            // Transaction 2 : persistance des chunks + mise à jour du statut
            transactionTemplate.executeWithoutResult(status -> {
                List<DocumentEntity> documents = chunks.stream()
                        .map(this::toDocumentEntity)
                        .toList();

                documentRepository.saveAll(documents);
                updateStatus(tempDocId, EmbeddingStatus.PROCESSED);

                log.info("Embedding terminé avec succès pour le document ID: {} ({} chunks persistés)",
                        tempDocId, documents.size());
            });

        } catch (Exception e) {
            log.error("Erreur lors du traitement d'embedding pour le document ID: {}", tempDocId, e);
            // Transaction indépendante : mise à jour du statut en ERREUR (commit garanti)
            updateStatusSafely(tempDocId, EmbeddingStatus.FAILED);
        }
    }

    private void updateStatus(Long tempDocId, EmbeddingStatus status) {
        var tempDoc = tempSourceDocumentRepository.findById(tempDocId)
                .orElseThrow(() -> new RuntimeException("Document temporaire introuvable: " + tempDocId));
        tempDoc.setStatus(status);
        tempSourceDocumentRepository.save(tempDoc);
    }

    /**
     * Met à jour le statut dans une transaction indépendante.
     * Ne propage pas d'exception pour ne pas masquer l'erreur originale.
     */
    private void updateStatusSafely(Long tempDocId, EmbeddingStatus status) {
        try {
            transactionTemplate.executeWithoutResult(txStatus -> updateStatus(tempDocId, status));
        } catch (Exception ex) {
            log.error("Impossible de mettre à jour le statut du document ID: {} vers {}", tempDocId, status, ex);
        }
    }

    private DocumentEntity toDocumentEntity(EmbeddingChunkResponse chunk) {
        var entity = new DocumentEntity();
        entity.setContent(chunk.content());
        entity.setEmbedding(chunk.embedding());
        entity.setMetadata(chunk.metadata());

        return entity;
    }

    /**
     * Record interne pour transporter les données du document entre les transactions.
     */
    private record DocumentData(String dataUrl, String userUuid, String fileName) {}
}

