package zas.admin.zec.backend.actions.upload;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import zas.admin.zec.backend.actions.authorize.UserService;

@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    @Qualifier("pyBackendWebClient")
    private final WebClient pyBackendWebClient;
    private final UserService userService;

    public DocumentController(WebClient pyBackendWebClient, UserService userService) {
        this.pyBackendWebClient = pyBackendWebClient;
        this.userService = userService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadDocument(@Valid DocumentUpload document, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        var builder = new MultipartBodyBuilder();
        builder.part("files", document.multipartFile().getResource())
                .filename(document.multipartFile().getOriginalFilename());

        pyBackendWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/apy/v1/indexing/upload_pdf_rag")
                        .queryParam("embed", document.embed())
                        .queryParam("user_uuid", userUuid)
                        .queryParam("language", document.lang())
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .toBodilessEntity()
                .block();

        return ResponseEntity.ok().build();
    }
}
