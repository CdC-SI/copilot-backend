package zas.admin.zec.backend.actions.upload;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record UploadRequest(
        @NotNull @ValidMultipartFile MultipartFile multipartFile,
        String lang,
        Boolean embed) {}
