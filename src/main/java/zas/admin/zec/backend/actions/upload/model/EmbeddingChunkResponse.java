package zas.admin.zec.backend.actions.upload.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Représente un chunk retourné par le service externe d'embedding.
 *
 * @param content   texte du chunk
 * @param embedding vecteur d'embedding du chunk
 * @param metadata  métadonnées associées au chunk
 */
public record EmbeddingChunkResponse(
        @JsonProperty("content") String content,
        @JsonProperty("embedding") String embedding,
        @JsonProperty("metadata") Map<String, String> metadata
) {}

