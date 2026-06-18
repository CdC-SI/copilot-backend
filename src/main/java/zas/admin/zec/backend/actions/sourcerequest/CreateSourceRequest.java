package zas.admin.zec.backend.actions.sourcerequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête pour créer une nouvelle demande de source.
 */
public record CreateSourceRequest(
    @NotBlank(message = "Le nom de la source est obligatoire")
    @Size(max = 255, message = "Le nom de la source ne peut pas dépasser 255 caractères")
    String sourceName,

    @NotBlank(message = "La description est obligatoire")
    @Size(max = 5000, message = "La description ne peut pas dépasser 5000 caractères")
    String description
) {}
