package zas.admin.zec.backend.actions.workspace;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO représentant un Workspace : un résumé, des questions / thématiques et l'ensemble des noms
 * de sources qui le composent.
 */
public record WorkspaceDto(
        Long id,
        String name,
        String description,
        List<String> hypotheticalQuestions,
        List<String> sources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
