package zas.admin.zec.backend.actions.upload.model;

import org.springframework.web.multipart.MultipartFile;

public record DocumentToUpload(
        MultipartFile file,
        String lang,
        boolean embed,
        boolean faqStore) {

    public DocumentToUpload(UploadRequest request) {
        this(
                request.multipartFile(),
                request.lang(),
                request.embed(),
                false
        );
    }

    public DocumentToUpload(MultipartFile file) {
        this(
                file,
                "fr",
                false,
                file.getOriginalFilename().startsWith("faq_q")
        );
    }
}
