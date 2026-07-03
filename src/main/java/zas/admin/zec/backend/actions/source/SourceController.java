package zas.admin.zec.backend.actions.source;

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
 * Controller d'administration des Sources.
 * Les lectures sont accessibles aux utilisateurs ; les mutations aux administrateurs.
 */
@Slf4j
@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @RequireUser
    @GetMapping
    public ResponseEntity<List<SourceDto>> getAll() {
        return ResponseEntity.ok(sourceService.getAll());
    }

    @RequireUser
    @GetMapping("/{name}")
    public ResponseEntity<SourceDto> getByName(@PathVariable String name) {
        return ResponseEntity.ok(sourceService.getByName(name));
    }

    @RequireAdmin
    @PostMapping
    public ResponseEntity<SourceDto> create(@RequestBody @Valid CreateSourceRequest request) {
        var created = sourceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @RequireAdmin
    @PutMapping("/{name}")
    public ResponseEntity<SourceDto> update(@PathVariable String name, @RequestBody @Valid UpdateSourceRequest request) {
        return ResponseEntity.ok(sourceService.update(name, request));
    }

    @RequireAdmin
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        sourceService.delete(name);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(SourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(SourceNotFoundException e) {
        log.warn("Source not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Source introuvable", "message", e.getMessage()));
    }

    @ExceptionHandler(SourceAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyExists(SourceAlreadyExistsException e) {
        log.warn("Source already exists: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Source déjà existante", "message", e.getMessage()));
    }
}
