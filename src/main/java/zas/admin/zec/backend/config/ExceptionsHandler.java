package zas.admin.zec.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ExceptionsHandler {

    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Map<String, Object> handleMaxSizeException(HttpServletRequest request, MaxUploadSizeExceededException exc) {
        log.warn("File too large", exc);
        return Map.of(
                "STATUS", HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "MESSAGE", "File too large",
                "PATH", request.getRequestURI()
        );
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, Object> handleValidationExceptions(HttpServletRequest request, MethodArgumentNotValidException e) {
        var errorMessage = getErrorMessage(e);
        log.warn(errorMessage, e);
        return Map.of(
                "STATUS", HttpStatus.BAD_REQUEST.value(),
                "MESSAGE", errorMessage,
                "PATH", request.getRequestURI()
        );
    }

    private String getErrorMessage(MethodArgumentNotValidException e) {
        return e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
    }
}
