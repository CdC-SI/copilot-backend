package zas.admin.zec.backend.actions.upload.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Enveloppe de la réponse du service externe d'embedding (format KServe).
 *
 * @param documents liste des chunks avec contenu, embedding et métadonnées
 */
public record EmbeddingServiceResponse(
        @JsonProperty("documents") List<EmbeddingChunkResponse> documents
) {}

