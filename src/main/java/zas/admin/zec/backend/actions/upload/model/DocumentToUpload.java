package zas.admin.zec.backend.actions.upload.model;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public record DocumentToUpload(
        byte[] content,
        String contentType,
        String name,
        String lang,
        boolean embed) {

    public DocumentToUpload(UploadRequest request) throws IOException {
        this(
                request.multipartFile().getBytes(),
                request.multipartFile().getContentType(),
                request.multipartFile().getOriginalFilename(),
                request.lang(),
                request.embed()
        );
    }

    public DocumentToUpload(MultipartFile file) throws IOException {
        this(
                file.getBytes(),
                file.getContentType(),
                file.getOriginalFilename(),
                "fr",
                false
        );
    }
}
