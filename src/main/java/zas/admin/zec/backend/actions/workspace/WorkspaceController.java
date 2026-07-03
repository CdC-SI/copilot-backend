package zas.admin.zec.backend.actions.workspace;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.config.security.RequireAdmin;
import zas.admin.zec.backend.config.security.RequireUser;

import java.util.List;
import java.util.Map;

/**
 * Controller d'administration des Workspaces.
 * Les lectures sont accessibles aux utilisateurs ; les mutations aux administrateurs.
 */
@Slf4j
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @RequireUser
    @GetMapping
    public ResponseEntity<List<WorkspaceDto>> getAll() {
        return ResponseEntity.ok(workspaceService.getAll());
    }

    @RequireUser
    @GetMapping("/{name}")
    public ResponseEntity<WorkspaceDto> getByName(@PathVariable String name) {
        return ResponseEntity.ok(workspaceService.getByName(name));
    }

    @RequireAdmin
    @PostMapping
    public ResponseEntity<WorkspaceDto> create(@RequestBody @Valid CreateWorkspaceRequest request) {
        var created = workspaceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @RequireAdmin
    @PutMapping("/{name}")
    public ResponseEntity<WorkspaceDto> update(@PathVariable String name, @RequestBody @Valid UpdateWorkspaceRequest request) {
        return ResponseEntity.ok(workspaceService.update(name, request));
    }

    @RequireAdmin
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        workspaceService.delete(name);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(WorkspaceNotFoundException e) {
        log.warn("Workspace not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Workspace introuvable", "message", e.getMessage()));
    }

    @ExceptionHandler(WorkspaceAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyExists(WorkspaceAlreadyExistsException e) {
        log.warn("Workspace already exists: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Workspace déjà existant", "message", e.getMessage()));
    }

    @ExceptionHandler(UnknownSourceException.class)
    public ResponseEntity<Map<String, String>> handleUnknownSource(UnknownSourceException e) {
        log.warn("Unknown source referenced: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Source inconnue", "message", e.getMessage()));
    }
}
