package zas.admin.zec.backend.actions.upload.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Requête envoyée au service externe d'embedding.
 *
 * @param dataUrl   les bytes en base64 du document
 * @param userUuid  l'identifiant de l'utilisateur
 * @param docTitle  le nom du document
 */
public record EmbeddingRequest(
        @JsonProperty("data_url") String dataUrl,
        @JsonProperty("user_uuid") String userUuid,
        @JsonProperty("doc_title") String docTitle
) {}

