package zas.admin.zec.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConfigController {
    @GetMapping("/debug/async-supported")
    public Map<String, Object> debugAsyncSupported(HttpServletRequest request) {
        return Map.of(
                "asyncSupported", request.isAsyncSupported(),
                "tomcatAsyncAttr", String.valueOf(
                        request.getAttribute("org.apache.catalina.ASYNC_SUPPORTED")
                )
        );
    }
}
