package zas.admin.zec.backend.tools;

import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;

import java.util.List;

/**
 * Résolution workspace &rarr; sources basée sur la base de données (tables {@code workspace} /
 * {@code source} / {@code workspace_source}). Remplace l'ancienne configuration statique
 * {@code workspace.sources} de {@code application-workspace.yml}.
 */
@Component
public class DbSourceResolver implements SourceResolver {

    private final WorkspaceService workspaceService;

    public DbSourceResolver(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public List<String> resolve(String workspace) {
        return workspaceService.resolveSources(workspace);
    }
}
