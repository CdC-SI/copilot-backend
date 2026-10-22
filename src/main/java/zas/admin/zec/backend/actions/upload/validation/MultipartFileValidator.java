package zas.admin.zec.backend.actions.upload.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.upload.model.SourceDocumentType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
public class MultipartFileValidator implements ConstraintValidator<ValidMultipartFile, MultipartFile> {

    private static final long LIMIT_SIZE_PER_FILE = 10 * 1024 * 1024L;
    private static final Tika TIKA = new Tika();
    private final List<String> allowedMimeTypes;

    public MultipartFileValidator() {
        allowedMimeTypes = SourceDocumentType.mimeTypes(SourceDocumentType.PDF);
    }

    public MultipartFileValidator(List<String> allowedMimeTypes) {
        this.allowedMimeTypes = allowedMimeTypes;
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null) {
            return false;
        }

        context.disableDefaultConstraintViolation();
        var mimeType = getMimeType(file);
        return isNotTooBig(file, context)
                && isAllowedMimeType(mimeType, context)
                && isMagicNumberMatchingExtension(file, mimeType, context);
    }

    private String getMimeType(MultipartFile file) {
        return SourceDocumentType.fromFilename(file.getOriginalFilename())
                .map(SourceDocumentType::mimeType)
                .orElse(null);
    }

    private boolean isNotTooBig(MultipartFile file, ConstraintValidatorContext context) {
        if (file.getSize() <= LIMIT_SIZE_PER_FILE) {
            return true;
        }

        context.buildConstraintViolationWithTemplate(UploadErrorCode.FILE_TOO_LARGE.name()).addConstraintViolation();
        return false;
    }
    private boolean isAllowedMimeType(String mimeType, ConstraintValidatorContext context) {
        if (mimeType != null && allowedMimeTypes.contains(mimeType)) {
            return true;
        }

        context.buildConstraintViolationWithTemplate(UploadErrorCode.FILE_TYPE_UNSUPPORTED.name()).addConstraintViolation();
        return false;
    }

    private boolean isMagicNumberMatchingExtension(MultipartFile file, String mimeType, ConstraintValidatorContext context) {
        var type = SourceDocumentType.fromMimeType(mimeType).orElse(null);
        if (type == null || !type.container().requiresContentCheck()) {
            return true;
        }

        try (InputStream is = file.getInputStream()) {
            String mimeTypeFromMagicNumber = TIKA.detect(is);
            log.info("File type detected: {}", mimeTypeFromMagicNumber);
            if (mimeType.equals(mimeTypeFromMagicNumber) || type.container().matches(mimeTypeFromMagicNumber)) {
                return true;
            }
        } catch (IOException e) {
            log.error("Tika parsing failure !", e);
        }

        context.buildConstraintViolationWithTemplate(UploadErrorCode.FILE_CONTENT_INVALID.name()).addConstraintViolation();
        return false;
    }
}
