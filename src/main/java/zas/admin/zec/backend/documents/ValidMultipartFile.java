package zas.admin.zec.backend.documents;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@ReportAsSingleViolation
@Constraint(validatedBy = MultipartFileValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface ValidMultipartFile {
    String message() default "Invalid file value";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
