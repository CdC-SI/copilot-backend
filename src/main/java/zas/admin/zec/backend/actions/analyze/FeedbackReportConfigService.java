package zas.admin.zec.backend.actions.analyze;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.actions.analyze.model.FeedbackReportConfig;
import zas.admin.zec.backend.persistence.entity.FeedbackReportConfigEntity;
import zas.admin.zec.backend.persistence.repository.FeedbackReportConfigRepository;

import java.util.Arrays;
import java.util.List;

/**
 * Gère la configuration globale (ligne unique) de planification du rapport hebdomadaire
 * des feedbacks : activation, expression cron, fuseau horaire, destinataires et fenêtre
 * d'analyse (nombre de jours pris en compte).
 */
@Slf4j
@Service
public class FeedbackReportConfigService {

    private static final String RECIPIENTS_SEPARATOR = ";";

    private final FeedbackReportConfigRepository repository;

    public FeedbackReportConfigService(FeedbackReportConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public FeedbackReportConfig get() {
        return toModel(getEntity());
    }

    @Transactional
    public FeedbackReportConfig update(FeedbackReportConfig config) {
        var entity = getEntity();
        entity.setEnabled(config.enabled());
        entity.setCronExpression(config.cronExpression());
        entity.setZoneId(config.zoneId());
        entity.setRecipients(String.join(RECIPIENTS_SEPARATOR, config.recipients()));
        entity.setLookbackDays(config.lookbackDays());
        repository.save(entity);

        log.info("Configuration du rapport hebdomadaire des feedbacks mise à jour : enabled={}, cron={}, zone={}, lookbackDays={}",
                config.enabled(), config.cronExpression(), config.zoneId(), config.lookbackDays());
        return toModel(entity);
    }

    FeedbackReportConfigEntity getEntity() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune configuration de rapport de feedback n'est présente en base"));
    }

    private FeedbackReportConfig toModel(FeedbackReportConfigEntity entity) {
        List<String> recipients = entity.getRecipients() == null || entity.getRecipients().isBlank()
                ? List.of()
                : Arrays.stream(entity.getRecipients().split(RECIPIENTS_SEPARATOR)).map(String::trim).toList();

        return new FeedbackReportConfig(entity.isEnabled(), entity.getCronExpression(), entity.getZoneId(),
                recipients, entity.getLookbackDays());
    }
}
