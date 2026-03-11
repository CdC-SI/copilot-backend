package zas.admin.zec.backend.actions.sourcerequest;

import ch.admin.zas.common.security.users.ZasUser;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.config.security.RequireAdmin;
import zas.admin.zec.backend.config.security.RequireZasUser;

import java.util.List;
import java.util.Map;

/**
 * Controller pour la gestion des demandes de sources.
 */
@Slf4j
@RestController
@RequestMapping("/api/source-requests")
public class SourceRequestController {

    private final SourceRequestService sourceRequestService;

    public SourceRequestController(SourceRequestService sourceRequestService) {
        this.sourceRequestService = sourceRequestService;
    }

    /**
     * Crée une nouvelle demande de source.
     * Accessible aux utilisateurs authentifiés.
     */
    @RequireZasUser
    @PostMapping
    public ResponseEntity<SourceRequestDto> createRequest(
            @RequestBody @Valid CreateSourceRequest request,
            Authentication authentication) {
        log.info("User '{}' creating new source request", authentication.getName());
        var visa = ((ZasUser) authentication.getPrincipal()).getTrigramme();
        var created = sourceRequestService.createRequest(request, visa);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Récupère les demandes de l'utilisateur connecté.
     * Accessible aux utilisateurs authentifiés.
     */
    @RequireZasUser
    @GetMapping("/my")
    public ResponseEntity<List<SourceRequestDto>> getMyRequests(
            @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        log.debug("User '{}' fetching their source requests", authentication.getName());
        var visa = ((ZasUser) authentication.getPrincipal()).getTrigramme();
        var requests = sourceRequestService.getUserRequests(visa, limit);
        return ResponseEntity.ok(requests);
    }

    /**
     * Récupère toutes les demandes de sources.
     * Accessible aux administrateurs uniquement.
     */
    @RequireAdmin
    @GetMapping
    public ResponseEntity<List<SourceRequestDto>> getAllRequests(
            @RequestParam(required = false) Integer limit) {
        log.debug("Admin fetching all source requests");
        var requests = sourceRequestService.getAllRequests(limit);
        return ResponseEntity.ok(requests);
    }

    /**
     * Récupère les demandes par statut.
     * Accessible aux administrateurs uniquement.
     */
    @RequireAdmin
    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<SourceRequestDto>> getRequestsByStatus(
            @PathVariable RequestStatus status) {
        log.debug("Admin fetching source requests with status: {}", status);
        var requests = sourceRequestService.getRequestsByStatus(status);
        return ResponseEntity.ok(requests);
    }

    /**
     * Récupère une demande par son ID.
     * Les utilisateurs peuvent accéder uniquement à leurs propres demandes.
     * Les administrateurs peuvent accéder à toutes les demandes.
     */
    @RequireZasUser
    @GetMapping("/{id}")
    public ResponseEntity<SourceRequestDto> getById(@PathVariable Long id, Authentication authentication) {
        log.debug("User '{}' fetching source request {}", authentication.getName(), id);
        var visa = ((ZasUser) authentication.getPrincipal()).getTrigramme();
        var request = sourceRequestService.getById(id, visa);
        return ResponseEntity.ok(request);
    }

    @RequireZasUser
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id, Authentication authentication) {
        var visa = ((ZasUser) authentication.getPrincipal()).getTrigramme();
        sourceRequestService.deleteById(id, visa);
        return ResponseEntity.ok().build();
    }

    /**
     * Récupère une demande par son ID (admin).
     * Accessible aux administrateurs uniquement, sans vérification de propriété.
     */
    @RequireAdmin
    @GetMapping("/admin/{id}")
    public ResponseEntity<SourceRequestDto> getByIdAdmin(@PathVariable Long id) {
        log.debug("Admin fetching source request {}", id);
        var request = sourceRequestService.getByIdAdmin(id);
        return ResponseEntity.ok(request);
    }

    /**
     * Met à jour le statut d'une demande.
     * Accessible aux administrateurs uniquement.
     */
    @RequireAdmin
    @PatchMapping("/{id}/status")
    public ResponseEntity<SourceRequestDto> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateStatusRequest request) {
        log.info("Admin updating status of source request {} to {}", id, request.status());
        var updated = sourceRequestService.updateStatus(id, request.status());
        return ResponseEntity.ok(updated);
    }

    /**
     * Gestion des exceptions spécifiques aux demandes de sources.
     */
    @ExceptionHandler(SourceRequestNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(SourceRequestNotFoundException e) {
        log.warn("Source request not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Demande introuvable", "message", e.getMessage()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTransition(InvalidStatusTransitionException e) {
        log.warn("Invalid status transition: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Transition de statut invalide", "message", e.getMessage()));
    }
}
