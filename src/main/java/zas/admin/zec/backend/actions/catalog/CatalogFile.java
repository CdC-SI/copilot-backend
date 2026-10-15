package zas.admin.zec.backend.actions.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Contenu d'un fichier YAML de catalogue : définitions des sources et des workspaces à créer
 * ou mettre à jour (upsert par {@code name}).
 *
 * <pre>{@code
 * version: 1
 * sources:
 *   - name: avs
 *     description: Assurance-vieillesse et survivants
 *     hypotheticalQuestions:
 *       - Quel est l'âge de référence ?
 * workspaces:
 *   - name: assurances-sociales
 *     description: Workspace généraliste
 *     sources: [avs]
 * }</pre>
 */
public record CatalogFile(
        @NotNull(message = "La version du format est obligatoire")
        Integer version,

        List<@Valid @NotNull(message = "Une entrée de source ne peut pas être vide") SourceEntry> sources,

        List<@Valid @NotNull(message = "Une entrée de workspace ne peut pas être vide") WorkspaceEntry> workspaces
) {

    public static final int CURRENT_VERSION = 1;

    public CatalogFile {
        sources = sources != null ? sources : List.of();
        workspaces = workspaces != null ? workspaces : List.of();
    }

    public record SourceEntry(
            @NotBlank(message = "Le nom de la source est obligatoire")
            @Size(max = 255, message = "Le nom de la source ne peut pas dépasser 255 caractères")
            String name,

            @Size(max = 10_000, message = "La description ne peut pas dépasser 10000 caractères")
            String description,

            List<@NotBlank(message = "Une question hypothétique ne peut pas être vide") String> hypotheticalQuestions
    ) {
        public SourceEntry {
            hypotheticalQuestions = hypotheticalQuestions != null ? hypotheticalQuestions : List.of();
        }
    }

    public record WorkspaceEntry(
            @NotBlank(message = "Le nom du workspace est obligatoire")
            @Size(max = 255, message = "Le nom du workspace ne peut pas dépasser 255 caractères")
            String name,

            @Size(max = 10_000, message = "La description ne peut pas dépasser 10000 caractères")
            String description,

            List<@NotBlank(message = "Une question hypothétique ne peut pas être vide") String> hypotheticalQuestions,

            List<@NotBlank(message = "Un nom de source ne peut pas être vide") String> sources
    ) {
        public WorkspaceEntry {
            hypotheticalQuestions = hypotheticalQuestions != null ? hypotheticalQuestions : List.of();
            sources = sources != null ? sources : List.of();
        }
    }
}
