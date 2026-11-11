package zas.admin.zec.backend.actions.upload.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réponse du service externe d'embedding pour {@code POST /jobs} et {@code GET /jobs/{id}}.
 *
 * @param jobId           identifiant du job
 * @param status          statut brut (voir {@link EmbeddingJobStatus})
 * @param pagesTotal      nombre total de pages
 * @param pagesDone       nombre de pages traitées
 * @param pagesFailed     numéros des pages en échec
 * @param error           détail de l'erreur si le job a échoué
 * @param errorCode       code d'erreur si le job a échoué
 * @param resultAvailable {@code true} si le résultat peut être récupéré via {@code GET /jobs/{id}/result}
 */
public record EmbeddingJobResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("status") String status,
        @JsonProperty("pages_total") Integer pagesTotal,
        @JsonProperty("pages_done") Integer pagesDone,
        @JsonProperty("pages_failed") List<Integer> pagesFailed,
        @JsonProperty("error") Object error,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("result_available") Boolean resultAvailable
) {
    public EmbeddingJobResponse {
        pagesFailed = pagesFailed == null ? List.of() : List.copyOf(pagesFailed);
    }

    public EmbeddingJobStatus jobStatus() {
        return EmbeddingJobStatus.from(status);
    }
}
