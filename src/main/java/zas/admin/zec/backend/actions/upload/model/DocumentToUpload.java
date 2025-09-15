package zas.admin.zec.backend.actions.upload.model;

import org.springframework.web.multipart.MultipartFile;

public record DocumentToUpload(
        MultipartFile file,
        String lang,
        boolean embed) {

    public DocumentToUpload(UploadRequest request) {
        this(
                request.multipartFile(),
                request.lang(),
                request.embed()
        );
    }

    public DocumentToUpload(MultipartFile file) {
        this(
                file,
                "fr",
                false
        );
    }
}
