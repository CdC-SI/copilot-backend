package zas.admin.zec.backend.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "py-backend")
public class PyBackendProperties {
    private String baseUrl;
}
