package zas.admin.zec.backend.actions.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requête de création d'un Workspace.
 */
public record CreateWorkspaceRequest(
        @NotBlank(message = "Le nom du workspace est obligatoire")
        @Size(max = 255, message = "Le nom du workspace ne peut pas dépasser 255 caractères")
        String name,

        @Size(max = 10_000, message = "La description ne peut pas dépasser 10000 caractères")
        String description,

        List<String> hypotheticalQuestions,

        List<String> sources
) {}
