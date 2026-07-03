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

    /**
     * @deprecated La liste des sources par workspace est désormais stockée en base
     * (tables {@code workspace} / {@code source} / {@code workspace_source}) et résolue via
     * {@code WorkspaceService}. Ce champ n'est plus utilisé et sera retiré.
     */
    @Deprecated(forRemoval = true)
    private Map<String, List<String>> sources = new LinkedHashMap<>();
}
