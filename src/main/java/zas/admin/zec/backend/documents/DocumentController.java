package zas.admin.zec.backend.documents;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import zas.admin.zec.backend.users.UserService;

import java.io.File;
import java.io.IOException;

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

    @PostMapping(value = "/upload")
    public Mono<ResponseEntity<String>> uploadDocument(
            @RequestPart(value = "files") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "language", defaultValue = "de") String language,
            @RequestParam(value = "embed", defaultValue = "true") boolean embed,
            Authentication authentication) {

        if (authorization != null && !authorization.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.status(403).body("Invalid token format"));
        }
        log.info("Starting document upload with content type: {}", file.getContentType());
        var userUuid = userService.getUuid(authentication.getName());
        log.info("User UUID: {}", userUuid);
        log.info("File name: {}", file.getOriginalFilename());
        log.info("Language: {}", language);
        log.info("Embed: {}", embed);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("files", new FileSystemResource(multipartToFile(file)));
        } catch (IOException e) {
            log.error("Error converting multipart file to file", e);
            return Mono.just(ResponseEntity.badRequest().body("Error converting file"));
        }
        body.add("embed", embed);
        body.add("user_uuid", userUuid);
        body.add("language", language);

        return pyBackendWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/apy/v1/indexing/upload_pdf_rag")
                        .queryParam("embed", embed)
                        .queryParam("user_uuid", userUuid)
                        .queryParam("language", language)
                        .build())
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set("Authorization", authorization);
                    }
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> log.info("Upload response: {}", response))
                .doOnError(error -> log.error("Error during upload", error))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error uploading document", e);
                    return Mono.just(ResponseEntity.badRequest().body("Error uploading document"));
                });
    }

    private File multipartToFile(MultipartFile multipart) throws IOException {
        File tempFile = File.createTempFile("temp", multipart.getOriginalFilename());
        multipart.transferTo(tempFile);
        return tempFile;
    }
}
