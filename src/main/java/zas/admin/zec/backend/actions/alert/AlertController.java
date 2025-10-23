package zas.admin.zec.backend.actions.alert;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.config.security.RequireAdmin;

import java.util.List;

@RequireAdmin
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<List<Alert>> getAllAlerts() {
        var alerts = alertService.getAllAlerts();
        return new ResponseEntity<>(alerts, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<Alert> createAlert(@RequestBody Alert alert) {
        var created = alertService.create(alert);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<Void> updateAlertExpiration(@PathVariable long id, @RequestBody UpdateExpiration updateRequest) {
        alertService.reactivate(id, updateRequest.expiresAt());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable long id) {
        alertService.delete(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
