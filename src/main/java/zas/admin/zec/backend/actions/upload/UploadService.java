package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.actions.upload.model.*;
import zas.admin.zec.backend.actions.upload.strategy.AdminDocUploadStrategyFactory;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class UploadService {

    private final AdminDocUploadStrategyFactory adminDocUploadStrategyFactory;
    private final TempSourceDocumentRepository sourceDocumentRepository;
    private final VectorStore vectorStore;
    private final TempSourceDocumentRepository tempSourceDocumentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentRetentionConfigService retentionConfigService;

    public UploadService(AdminDocUploadStrategyFactory adminDocUploadStrategyFactory,
                         TempSourceDocumentRepository sourceDocumentRepository,
                         VectorStore vectorStore,
                         TempSourceDocumentRepository tempSourceDocumentRepository,
                         ApplicationEventPublisher eventPublisher,
                         DocumentRetentionConfigService retentionConfigService) {

        this.adminDocUploadStrategyFactory = adminDocUploadStrategyFactory;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.vectorStore = vectorStore;
        this.tempSourceDocumentRepository = tempSourceDocumentRepository;
        this.eventPublisher = eventPublisher;
        this.retentionConfigService = retentionConfigService;
    }

    public record Doc(String filename, ByteArrayResource content) {}
    public Doc download(String filename) {
        TempSourceDocumentEntity byFileName = sourceDocumentRepository.findByFileName(filename)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + filename));

        return new Doc(filename, new ByteArrayResource(byFileName.getContent()));
    }

    @Transactional
    public void uploadPersonalDocument(DocumentToUpload document, String userUuid) {
        try {
            TempSourceDocumentEntity personalDoc = new TempSourceDocumentEntity();
            personalDoc.setFileName(document.file().getOriginalFilename());
            personalDoc.setContent(document.file().getBytes());
            personalDoc.setUserUuid(userUuid);
            personalDoc.setUploadedAt(LocalDateTime.now());
            personalDoc.setStatus(EmbeddingStatus.PENDING);
            personalDoc.setAvailabilityStatus(AvailabilityStatus.ACTIVE);

            var savedDoc = tempSourceDocumentRepository.save(personalDoc);

            log.info("Document personnel {} persisté, embedding asynchrone déclenché après commit",
                    document.file().getOriginalFilename());

            eventPublisher.publishEvent(new PersonalDocumentUploadedEvent(savedDoc.getId()));
        } catch (IOException e) {
            throw new UploadException(document.file().getOriginalFilename(),
                    "Error while uploading personal document", e);
        }
    }

    public List<PersonalDoc> getUserPersonalDocs(String userUuid) {
        var retentionConfig = retentionConfigService.get();
        return tempSourceDocumentRepository.findAllByUserUuid(userUuid)
                .stream()
                .map(doc -> new PersonalDoc(
                        doc.getFileName(),
                        doc.getUploadedAt(),
                        doc.getStatus(),
                        doc.getAvailabilityStatus(),
                        computeTimeToLiveInDays(doc, retentionConfig)))
                .toList();
    }

    /**
     * Calcule le nombre de jours restants avant la suppression physique du document.
     * <ul>
     *   <li>ACTIF : {@code uploadedAt + joursAvantArchivage + joursAvantSuppression - now}</li>
     *   <li>ARCHIVÉ : {@code archivedAt + joursAvantSuppression - now}</li>
     * </ul>
     * La valeur est bornée à 0 (jamais négative).
     */
    private Long computeTimeToLiveInDays(TempSourceDocumentEntity doc, DocumentRetentionConfig config) {
        LocalDateTime deletionDate = switch (doc.getAvailabilityStatus()) {
            case ARCHIVED -> doc.getArchivedAt() != null
                    ? doc.getArchivedAt().plusDays(config.daysBeforeDeletion())
                    : null;
            case ACTIVE -> doc.getUploadedAt() != null
                    ? doc.getUploadedAt()
                        .plusDays(config.daysBeforeArchival())
                        .plusDays(config.daysBeforeDeletion())
                    : null;
        };

        if (deletionDate == null) {
            return null;
        }

        long days = Duration.between(LocalDateTime.now(), deletionDate).toDays();
        return Math.max(0, days);
    }

    @Transactional
    public void reactivatePersonalDocument(String filename, String userUuid) {
        var docEntity = tempSourceDocumentRepository.findByFileNameAndUserUuid(filename, userUuid)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Document %s not found", filename)));

        if (docEntity.getAvailabilityStatus() == AvailabilityStatus.ACTIVE) {
            throw new IllegalStateException(String.format("Document %s is already active", filename));
        }

        docEntity.setAvailabilityStatus(AvailabilityStatus.ACTIVE);
        docEntity.setArchivedAt(null);
        docEntity.setUploadedAt(LocalDateTime.now());
        tempSourceDocumentRepository.save(docEntity);

        log.info("Document personnel {} réactivé pour l'utilisateur {}", filename, userUuid);
    }

    @Transactional
    public void deleteUserPersonalDocument(String filename, String userUuid) {
        var docEntity = tempSourceDocumentRepository.findByFileNameAndUserUuid(filename, userUuid)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Document %s not found", filename)));

        tempSourceDocumentRepository.delete(docEntity);
        vectorStore.delete(buildUserFileFilter(userUuid, filename));
    }

    public void uploadAdminDocuments(List<DocumentToUpload> documents) {
        documents.forEach(doc -> adminDocUploadStrategyFactory.getUploadStrategy(doc).upload(doc));
    }


    private Filter.Expression buildUserFileFilter(String userUuid, String filename) {
        var expressionBuilder = new FilterExpressionBuilder();
        return expressionBuilder.and(
                expressionBuilder.eq("user_uuid", userUuid),
                expressionBuilder.eq("title", filename)
        ).build();
    }
}
