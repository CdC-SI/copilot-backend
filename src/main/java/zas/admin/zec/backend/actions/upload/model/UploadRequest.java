package zas.admin.zec.backend.actions.upload.model;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.upload.validation.ValidMultipartFile;

public record UploadRequest(
        @NotNull @ValidMultipartFile MultipartFile multipartFile,
        String lang,
        Boolean embed) {}
