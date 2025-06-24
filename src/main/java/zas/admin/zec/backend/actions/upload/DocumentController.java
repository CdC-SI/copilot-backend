package zas.admin.zec.backend.actions.upload;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.model.UploadRequest;
import zas.admin.zec.backend.actions.upload.validation.ValidMultipartFileList;
import zas.admin.zec.backend.config.RequireAdmin;
import zas.admin.zec.backend.config.RequireInternalUser;
import zas.admin.zec.backend.config.RequireUser;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final UserService userService;
    private final UploadService uploadService;

    public DocumentController(UserService userService, UploadService uploadService) {
        this.userService = userService;
        this.uploadService = uploadService;
    }

    @RequireInternalUser
    @GetMapping
    public ResponseEntity<Resource> getDocuments(@RequestParam String filename) {
        var download = uploadService.download(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=%s".formatted(download.filename()))
                .contentType(MediaType.APPLICATION_PDF)
                .body(download.content());
    }

    @RequireUser
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadDocument(@Valid UploadRequest request, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        try {
            uploadService.uploadPersonalDocument(new DocumentToUpload(request), userUuid);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }

    @RequireAdmin
    @PostMapping(value = "/upload-admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadDocumentAdmin(@Valid @ValidMultipartFileList List<MultipartFile> documents, Authentication authentication) {
        var toUploadList = documents.stream()
                .map(file -> {
                    try {
                        return new DocumentToUpload(file);
                    } catch (IOException e) {
                        log.error("Error processing file {}: {}", file.getOriginalFilename(), e.getMessage());
                        return null;
                    }
                })
                .filter(Predicate.not(Objects::isNull))
                .toList();

        uploadService.uploadAdminDocuments(toUploadList);
        return ResponseEntity.ok().build();
    }
}
