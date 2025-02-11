package zas.admin.zec.backend.documents;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLConnection;
import java.util.List;

@Slf4j
public class MultipartFileValidator implements ConstraintValidator<ValidMultipartFile, MultipartFile> {

    private static final long LIMIT_SIZE_PER_FILE = 10 * 1024 * 1024L;
    private static final List<String> ALLOWED_MIME_TYPES = List.of("application/pdf");
    
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
        var name = file.getOriginalFilename();
        if (name != null) {
            return URLConnection.guessContentTypeFromName(name);
        }
        return null;
    }

    private boolean isNotTooBig(MultipartFile file, ConstraintValidatorContext context) {
        if (file.getSize() <= LIMIT_SIZE_PER_FILE) {
            return true;
        }

        context.buildConstraintViolationWithTemplate("FILE_TOO_LARGE").addConstraintViolation();
        return false;
    }
    private boolean isAllowedMimeType(String mimeType, ConstraintValidatorContext context) {
        if (mimeType != null && ALLOWED_MIME_TYPES.contains(mimeType)) {
            return true;
        }

        context.buildConstraintViolationWithTemplate("FILE_TYPE_UNSUPPORTED").addConstraintViolation();
        return false;
    }

    private boolean isMagicNumberMatchingExtension(MultipartFile file, String mimeType, ConstraintValidatorContext context) {
        var tika = new Tika();
        try {
            String mimeTypeFromMagicNumber = tika.detect(file.getInputStream());
            log.info("File type detected: {}", mimeTypeFromMagicNumber);
            if (ALLOWED_MIME_TYPES.contains(mimeTypeFromMagicNumber) && mimeType.equals(mimeTypeFromMagicNumber)) {
                return true;
            }
        } catch (IOException e) {
            log.error("Tika parsing failure !", e);
        }

        context.buildConstraintViolationWithTemplate("FILE_CONTENT_INVALID").addConstraintViolation();
        return false;
    }
}
