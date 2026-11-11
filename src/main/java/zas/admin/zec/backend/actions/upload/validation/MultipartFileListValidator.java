package zas.admin.zec.backend.actions.upload.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.upload.model.SourceDocumentType;

import java.util.List;

public class MultipartFileListValidator implements ConstraintValidator<ValidMultipartFileList, List<MultipartFile>> {

    private final MultipartFileValidator singleFileValidator =
            new MultipartFileValidator(SourceDocumentType.mimeTypes(SourceDocumentType.values()));

    @Override
    public boolean isValid(List<MultipartFile> files, ConstraintValidatorContext context) {
        if (files == null || files.isEmpty()) {
            return false;
        }

        for (MultipartFile file : files) {
            if (!singleFileValidator.isValid(file, context)) {
                return false;
            }
        }
        return true;
    }
}
