package zas.admin.zec.backend.actions.workspace;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requête de mise à jour d'un Workspace. Le nom est immuable et n'est donc pas modifiable ici.
 */
public record UpdateWorkspaceRequest(
        @Size(max = 10_000, message = "La description ne peut pas dépasser 10000 caractères")
        String description,

        List<String> hypotheticalQuestions,

        List<String> sources
) {}
