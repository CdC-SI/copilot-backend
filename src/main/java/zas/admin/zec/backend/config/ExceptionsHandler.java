package zas.admin.zec.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import zas.admin.zec.backend.actions.upload.validation.UploadException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ExceptionsHandler {

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public Map<String, Object> handleAccessDeniedException(HttpServletRequest request, AccessDeniedException exc) {
        return body(HttpStatus.FORBIDDEN, "Access denied", request, null);
    }

    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Map<String, Object> handleMaxSizeException(HttpServletRequest request, MaxUploadSizeExceededException exc) {
        log.warn("File too large", exc);
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", request, null);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, Object> handleValidationExceptions(HttpServletRequest request, MethodArgumentNotValidException e) {
        var errorMessage = joinMessages(e.getBindingResult().getAllErrors());
        log.warn(errorMessage, e);
        return body(HttpStatus.BAD_REQUEST, errorMessage, request, null);
    }

    /**
     * Validation des paramètres de méthode (ex. {@code @ValidMultipartFileList List<MultipartFile>}).
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Map<String, Object> handleMethodValidationExceptions(HttpServletRequest request, HandlerMethodValidationException e) {
        var errorMessage = joinMessages(e.getAllErrors());
        log.warn(errorMessage, e);
        return body(HttpStatus.BAD_REQUEST, errorMessage, request, null);
    }

    @ExceptionHandler(UploadException.class)
    public ResponseEntity<Map<String, Object>> handleUploadException(HttpServletRequest request, UploadException e) {
        var status = e.getErrorCode().getHttpStatus();
        if (status.is5xxServerError()) {
            log.error("Upload failed for file {} ({})", e.getFileName(), e.getErrorCode(), e);
        } else {
            log.warn("Upload rejected for file {} ({}): {}", e.getFileName(), e.getErrorCode(), e.getMessage());
        }
        return ResponseEntity.status(status).body(body(status, e.getErrorCode().name(), request, e.getFileName()));
    }

    private static String joinMessages(Collection<? extends MessageSourceResolvable> errors) {
        return errors.stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static Map<String, Object> body(HttpStatus status, String message, HttpServletRequest request, String fileName) {
        var body = new LinkedHashMap<String, Object>();
        body.put("STATUS", status.value());
        body.put("MESSAGE", message);
        body.put("PATH", request.getRequestURI());
        if (fileName != null) {
            body.put("FILENAME", fileName);
        }
        return body;
    }
}
