package zas.admin.zec.backend.actions.upload;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zas.admin.zec.backend.actions.authorize.UserService;

import java.io.IOException;

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
}
