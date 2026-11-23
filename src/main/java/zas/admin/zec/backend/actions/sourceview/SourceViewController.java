package zas.admin.zec.backend.actions.sourceview;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.security.RequireUser;

import java.nio.charset.StandardCharsets;

/**
 * Sources citées dans les réponses (panneau des sources du chat).
 *
 * <ul>
 *   <li>{@code GET /api/source-documents/{id}} : métadonnées, extrait et mode d'affichage ({@link SourceView}) ;</li>
 *   <li>{@code GET /api/source-documents/{id}/file} : fichier d'origine, en ligne pour un PDF (visionneuse), en
 *       pièce jointe sinon ; {@code nosniff} et {@code CSP: sandbox} car il s'agit de contenu déposé par des
 *       utilisateurs.</li>
 * </ul>
 * Une source inconnue ou inaccessible donne 404. ({@code /api/sources/{name}} désigne les sources de workspace.)
 */
@Slf4j
@RestController
@RequireUser
@RequestMapping("/api/source-documents")
public class SourceViewController {

    private final UserService userService;
    private final SourceViewService sourceViewService;

    public SourceViewController(UserService userService, SourceViewService sourceViewService) {
        this.userService = userService;
        this.sourceViewService = sourceViewService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<SourceView> getSource(@PathVariable String id, Authentication authentication) {
        var userId = userService.getUuid(authentication.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sourceViewService.view(id, userId));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> getSourceFile(@PathVariable String id, Authentication authentication) {
        var userId = userService.getUuid(authentication.getName());
        var file = sourceViewService.file(id, userId);
        var disposition = (file.isPdf() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .cacheControl(CacheControl.noStore())
                .contentType(file.mediaType())
                .body(file.content());
    }

    @ExceptionHandler(SourceViewNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(SourceViewNotFoundException e) {
        log.debug(e.getMessage());
        return ResponseEntity.notFound().build();
    }
}
