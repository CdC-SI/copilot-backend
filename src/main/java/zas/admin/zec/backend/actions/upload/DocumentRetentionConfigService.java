package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.actions.upload.model.DocumentRetentionConfig;
import zas.admin.zec.backend.persistence.entity.DocumentRetentionConfigEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRetentionConfigRepository;

/**
 * Gère la configuration globale (ligne unique) des durées de rétention des documents personnels :
 * délai avant archivage (depuis l'upload) et délai avant suppression (depuis l'archivage).
 */
@Slf4j
@Service
public class DocumentRetentionConfigService {

    private final DocumentRetentionConfigRepository repository;

    public DocumentRetentionConfigService(DocumentRetentionConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DocumentRetentionConfig get() {
        var entity = getEntity();
        return new DocumentRetentionConfig(entity.getDaysBeforeArchival(), entity.getDaysBeforeDeletion());
    }

    @Transactional
    public DocumentRetentionConfig update(DocumentRetentionConfig config) {
        var entity = getEntity();
        entity.setDaysBeforeArchival(config.daysBeforeArchival());
        entity.setDaysBeforeDeletion(config.daysBeforeDeletion());
        repository.save(entity);

        log.info("Configuration de rétention mise à jour : archivage={}j, suppression={}j",
                config.daysBeforeArchival(), config.daysBeforeDeletion());
        return new DocumentRetentionConfig(entity.getDaysBeforeArchival(), entity.getDaysBeforeDeletion());
    }

    DocumentRetentionConfigEntity getEntity() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune configuration de rétention des documents n'est présente en base"));
    }
}
