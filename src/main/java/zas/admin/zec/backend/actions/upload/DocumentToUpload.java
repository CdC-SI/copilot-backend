package zas.admin.zec.backend.actions.upload;

import java.io.IOException;

public record DocumentToUpload(
        byte[] content,
        String name,
        String lang,
        boolean embed) {

    public DocumentToUpload(UploadRequest request) throws IOException {
        this(
                request.multipartFile().getBytes(),
                request.multipartFile().getOriginalFilename(),
                request.lang(),
                request.embed()
        );
    }
}
