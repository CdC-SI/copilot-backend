package zas.admin.zec.backend.actions.alert;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.entity.AlertEntity;
import zas.admin.zec.backend.persistence.repository.AlertRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public Alert create(Alert alert) {
        AlertEntity entity = new AlertEntity();
        entity.setLevel(alert.level());
        entity.setTextFr(defaultAlertTextKey(alert.textFr()));
        entity.setTextDe(defaultAlertTextKey(alert.textDe()));
        entity.setTextIt(defaultAlertTextKey(alert.textIt()));
        entity.setExpiresAt(defaultExpiration(alert.expiresAt()));

        return entityToAlert(alertRepository.save(entity));
    }

    public List<Alert> getAllAlerts() {
        return alertRepository.findAll()
                .stream()
                .map(this::entityToAlert)
                .toList();
    }

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByExpiresAtAfter(LocalDateTime.now())
                .stream()
                .map(this::entityToAlert)
                .toList();
    }

    public void delete(long id) {
        alertRepository.deleteById(id);
    }

    public void reactivate(long id, LocalDateTime newExpirationDate) {
        alertRepository.findById(id).ifPresent(alert -> {
            alert.setExpiresAt(newExpirationDate);
            alertRepository.save(alert);
        });
    }

    private Alert entityToAlert(AlertEntity entity) {
        return new Alert(entity.getId(), entity.getLevel(), entity.getTextFr(), entity.getTextDe(), entity.getTextIt(), entity.getExpiresAt());
    }

    private String defaultAlertTextKey(String text) {
        return text == null
                ? "alert.default.text"
                : text;
    }

    private LocalDateTime defaultExpiration(LocalDateTime expiration) {
        return expiration == null
                ? LocalDateTime.now().plusHours(2)
                : expiration;
    }
}
