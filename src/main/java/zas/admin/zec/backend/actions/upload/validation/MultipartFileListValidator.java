package zas.admin.zec.backend.actions.upload.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class MultipartFileListValidator implements ConstraintValidator<ValidMultipartFileList, List<MultipartFile>> {

    private final MultipartFileValidator singleFileValidator =
            new MultipartFileValidator(List.of("application/pdf", "text/csv"));

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
