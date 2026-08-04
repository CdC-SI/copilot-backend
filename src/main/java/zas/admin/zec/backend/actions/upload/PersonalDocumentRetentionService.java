package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.actions.upload.model.AvailabilityStatus;
import zas.admin.zec.backend.actions.upload.model.DocumentRetentionConfig;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service planifié gérant le cycle de vie des documents personnels :
 * <ol>
 *   <li>ACTIF → ARCHIVÉ après {@code daysBeforeArchival} jours depuis l'upload.</li>
 *   <li>ARCHIVÉ → suppression physique (entité + chunks du vector store) après
 *       {@code daysBeforeDeletion} jours depuis l'archivage.</li>
 * </ol>
 * Seuls les documents rattachés à un utilisateur ({@code user_uuid} non nul) sont concernés ;
 * les documents publics/admin (sans {@code user_uuid}) ne sont jamais purgés.
 */
@Slf4j
@Service
public class PersonalDocumentRetentionService {

    private static final String META_TITLE = "title";
    private static final String META_USER_UUID = "user_uuid";

    private final TempSourceDocumentRepository tempSourceDocumentRepository;
    private final DocumentRetentionConfigService retentionConfigService;
    private final VectorStore vectorStore;

    public PersonalDocumentRetentionService(TempSourceDocumentRepository tempSourceDocumentRepository,
                                            DocumentRetentionConfigService retentionConfigService,
                                            VectorStore vectorStore) {

        this.tempSourceDocumentRepository = tempSourceDocumentRepository;
        this.retentionConfigService = retentionConfigService;
        this.vectorStore = vectorStore;
    }

    /**
     * Exécuté une fois par jour à minuit : archive les documents actifs expirés puis
     * supprime définitivement les documents archivés expirés.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runDailyLifecycle() {
        log.info("Démarrage du cycle de vie quotidien des documents personnels");
        var config = retentionConfigService.get();
        archiveExpired(config);
        deleteExpired(config);
        log.info("Cycle de vie quotidien des documents personnels terminé");
    }

    @Transactional
    public void archiveExpired(DocumentRetentionConfig config) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime uploadedAtThreshold = now.minusDays(config.daysBeforeArchival());

        List<TempSourceDocumentEntity> toArchive = tempSourceDocumentRepository
                .findAllByAvailabilityStatusAndUserUuidNotNullAndUploadedAtBefore(
                        AvailabilityStatus.ACTIVE, uploadedAtThreshold);

        for (var doc : toArchive) {
            doc.setAvailabilityStatus(AvailabilityStatus.ARCHIVED);
            doc.setArchivedAt(now);
        }
        tempSourceDocumentRepository.saveAll(toArchive);

        log.info("{} document(s) personnel(s) archivé(s)", toArchive.size());
    }

    @Transactional
    public void deleteExpired(DocumentRetentionConfig config) {
        LocalDateTime archivedAtThreshold = LocalDateTime.now().minusDays(config.daysBeforeDeletion());

        List<TempSourceDocumentEntity> toDelete = tempSourceDocumentRepository
                .findAllByAvailabilityStatusAndUserUuidNotNullAndArchivedAtBefore(
                        AvailabilityStatus.ARCHIVED, archivedAtThreshold);

        for (var doc : toDelete) {
            vectorStore.delete(buildUserFileFilter(doc.getUserUuid(), doc.getFileName()));
            tempSourceDocumentRepository.delete(doc);
            log.info("Document personnel {} (utilisateur {}) supprimé définitivement",
                    doc.getFileName(), doc.getUserUuid());
        }

        log.info("{} document(s) personnel(s) supprimé(s) définitivement", toDelete.size());
    }

    private Filter.Expression buildUserFileFilter(String userUuid, String filename) {
        var expressionBuilder = new FilterExpressionBuilder();
        return expressionBuilder.and(
                expressionBuilder.eq(META_USER_UUID, userUuid),
                expressionBuilder.eq(META_TITLE, filename)
        ).build();
    }
}
