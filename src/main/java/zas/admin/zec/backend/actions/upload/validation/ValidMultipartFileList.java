package zas.admin.zec.backend.actions.upload.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Constraint(validatedBy = MultipartFileListValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface ValidMultipartFileList {
    String message() default "Invalid file(s) in the list";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
