package zas.admin.zec.backend.actions.upload.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse du service externe d'embedding pour {@code POST /jobs} et {@code GET /jobs/{id}}.
 *
 * @param jobId       identifiant du job
 * @param status      statut brut (voir {@link EmbeddingJobStatus})
 * @param pagesTotal  nombre total de pages
 * @param pagesDone   nombre de pages traitées
 * @param pagesFailed nombre de pages en échec
 * @param error       détail de l'erreur si le job a échoué
 */
public record EmbeddingJobResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("status") String status,
        @JsonProperty("pages_total") Integer pagesTotal,
        @JsonProperty("pages_done") Integer pagesDone,
        @JsonProperty("pages_failed") Integer pagesFailed,
        @JsonProperty("error") Object error
) {
    public EmbeddingJobStatus jobStatus() {
        return EmbeddingJobStatus.from(status);
    }
}
