package zas.admin.zec.backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "workspace")
public class WorkspaceProperties {
    private String defaultWorkspace;
}
