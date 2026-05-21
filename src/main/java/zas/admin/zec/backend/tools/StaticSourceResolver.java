package zas.admin.zec.backend.tools;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.config.properties.WorkspaceProperties;

import java.util.List;

@Component
@Primary
public class StaticSourceResolver implements SourceResolver {

    private final WorkspaceProperties workspaceProperties;

    public StaticSourceResolver(WorkspaceProperties workspaceProperties) {
        this.workspaceProperties = workspaceProperties;
    }

    @Override
    public List<String> resolve(String workspace) {
        return workspaceProperties.getSources().getOrDefault(workspace, List.of());
    }
}

