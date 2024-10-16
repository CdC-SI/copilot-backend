package zas.admin.zec.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "py-backend")
public class PyBackendProperties {
    private String baseUrl;
}
