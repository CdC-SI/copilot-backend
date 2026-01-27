package zas.admin.zec.backend.actions.alert;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.config.security.RequireAdmin;
import zas.admin.zec.backend.config.security.RequireUser;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @RequireUser
    @GetMapping(path = "/active")
    public ResponseEntity<List<Alert>> getActiveAlerts() {
        var alerts = alertService.getActiveAlerts();
        return new ResponseEntity<>(alerts, HttpStatus.OK);
    }

    @RequireAdmin
    @GetMapping(path = "/all")
    public ResponseEntity<List<Alert>> getAllAlerts() {
        var alerts = alertService.getAllAlerts();
        return new ResponseEntity<>(alerts, HttpStatus.OK);
    }

    @RequireAdmin
    @PostMapping
    public ResponseEntity<Alert> createAlert(@RequestBody Alert alert) {
        var created = alertService.create(alert);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @RequireAdmin
    @PutMapping(path = "/{id}")
    public ResponseEntity<Void> updateAlertExpiration(@PathVariable long id, @RequestBody UpdateExpiration updateRequest) {
        alertService.reactivate(id, updateRequest.expiresAt());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequireAdmin
    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable long id) {
        alertService.delete(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
