package zas.admin.zec.backend.actions.source;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requête de mise à jour d'une Source. Le nom d'une source est immuable (il sert de clé vers
 * les chunks de {@code vector_store}) et n'est donc pas modifiable ici.
 */
public record UpdateSourceRequest(
        @Size(max = 10_000, message = "La description ne peut pas dépasser 10000 caractères")
        String description,

        List<String> hypotheticalQuestions
) {}
