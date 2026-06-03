package zas.admin.zec.backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "workspace")
public class WorkspaceProperties {
    private String defaultWorkspace;
    private Map<String, List<String>> sources = new LinkedHashMap<>();
}
