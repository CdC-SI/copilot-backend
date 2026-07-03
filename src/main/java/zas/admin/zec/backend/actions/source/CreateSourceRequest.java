package zas.admin.zec.backend.actions.source;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requête de création d'une Source.
 */
public record CreateSourceRequest(
        @NotBlank(message = "Le nom de la source est obligatoire")
        @Size(max = 255, message = "Le nom de la source ne peut pas dépasser 255 caractères")
        String name,

        @Size(max = 10_000, message = "La description ne peut pas dépasser 10000 caractères")
        String description,

        List<String> hypotheticalQuestions
) {}
