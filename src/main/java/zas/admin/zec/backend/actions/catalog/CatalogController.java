package zas.admin.zec.backend.actions.catalog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.config.security.RequireAdmin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Import / export YAML du catalogue (sources + workspaces), réservé aux administrateurs.
 * Permet de répliquer la configuration d'un environnement à l'autre en une seule opération.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogController {

    static final long MAX_FILE_SIZE = 1024 * 1024;
    static final MediaType APPLICATION_YAML = new MediaType("application", "yaml", StandardCharsets.UTF_8);

    private final CatalogService catalogService;
    private final CatalogYamlMapper yamlMapper;

    public CatalogController(CatalogService catalogService, CatalogYamlMapper yamlMapper) {
        this.catalogService = catalogService;
        this.yamlMapper = yamlMapper;
    }

    @RequireAdmin("catalog import")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CatalogImportReport> importCatalog(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidCatalogException("Le fichier est vide");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidCatalogException("Le fichier dépasse la taille maximale autorisée (1 Mo)");
        }
        try (var input = file.getInputStream()) {
            var catalog = yamlMapper.parse(input);
            return ResponseEntity.ok(catalogService.importCatalog(catalog));
        }
    }

    @RequireAdmin("catalog export")
    @GetMapping("/export")
    public ResponseEntity<String> exportCatalog() {
        var yaml = yamlMapper.write(catalogService.exportCatalog());
        return ResponseEntity.ok()
                .contentType(APPLICATION_YAML)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("catalog.yaml").build().toString())
                .body(yaml);
    }

    @ExceptionHandler(InvalidCatalogException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCatalog(InvalidCatalogException e) {
        log.warn("Import de catalogue refusé : {}", e.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Fichier de catalogue invalide", "messages", List.copyOf(e.getErrors())));
    }
}
