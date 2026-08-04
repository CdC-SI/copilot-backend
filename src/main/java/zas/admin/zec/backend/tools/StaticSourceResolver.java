package zas.admin.zec.backend.tools;

import org.springframework.stereotype.Component;
import zas.admin.zec.backend.config.properties.WorkspaceProperties;

import java.util.List;

/**
 * @deprecated Remplacé par {@link DbSourceResolver} qui lit la configuration des workspaces
 * depuis la base de données. Cette implémentation basée sur {@link WorkspaceProperties}
 * (application-workspace.yml) sera retirée.
 */
@Deprecated(forRemoval = true)
@Component
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

