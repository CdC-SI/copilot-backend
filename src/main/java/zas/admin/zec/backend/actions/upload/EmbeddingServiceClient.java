package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import zas.admin.zec.backend.actions.upload.model.EmbeddingChunkResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingJobResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingServiceResponse;
import zas.admin.zec.backend.config.properties.EmbeddingServiceProperties;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Client HTTP pour l'API asynchrone du service externe d'embedding de documents :
 * <ol>
 *   <li>{@code POST /jobs} : soumission du PDF (multipart) → {@code job_id}</li>
 *   <li>{@code GET /jobs/{id}} : suivi du statut</li>
 *   <li>{@code GET /jobs/{id}/result} : chunks avec leurs embeddings pré-calculés</li>
 *   <li>{@code DELETE /jobs/{id}} : annulation</li>
 * </ol>
 * Les erreurs HTTP / réseau sont traduites en {@link EmbeddingServiceException}.
 */
@Slf4j
@Service
public class EmbeddingServiceClient {

    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");

    private final WebClient webClient;
    private final Duration requestTimeout;

    public EmbeddingServiceClient(@Qualifier("clientBuilderForInternalCalls") WebClient.Builder clientBuilder,
                                  EmbeddingServiceProperties properties) {
        this.webClient = clientBuilder.clone()
                .baseUrl(properties.baseUrl())
                .codecs(codecs -> codecs.defaultCodecs()
                        .maxInMemorySize(Math.toIntExact(properties.maxResponseSize().toBytes())))
                .build();
        this.requestTimeout = properties.requestTimeout();
    }

    /**
     * Soumet un PDF au traitement asynchrone.
     *
     * @return la réponse contenant le {@code job_id}
     */
    public EmbeddingJobResponse submit(byte[] pdf, String userUuid, String docTitle) {
        log.info("Soumission du document {} au service externe d'embedding", docTitle);

        var multipart = new MultipartBodyBuilder();
        multipart.part("file", new ByteArrayResource(pdf))
                .filename(docTitle)
                .contentType(MediaType.APPLICATION_PDF);
        multipart.part("user_uuid", userUuid);
        multipart.part("doc_title", docTitle);

        var response = call(webClient.post()
                .uri("/jobs")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, EmbeddingServiceClient::toException)
                .bodyToMono(EmbeddingJobResponse.class), "submit " + docTitle);

        if (response == null || response.jobId() == null) {
            throw new EmbeddingServiceException(null, null, "Réponse sans job_id pour le document " + docTitle);
        }
        return response;
    }

    public EmbeddingJobResponse status(String jobId, String userUuid) {
        var response = call(webClient.get()
                .uri(uri -> uri.path("/jobs/{id}").queryParam("user_uuid", userUuid).build(jobId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, EmbeddingServiceClient::toException)
                .bodyToMono(EmbeddingJobResponse.class), "status " + jobId);

        if (response == null) {
            throw new EmbeddingServiceException(null, null, "Réponse de statut vide pour le job " + jobId);
        }
        return response;
    }

    public List<EmbeddingChunkResponse> result(String jobId, String userUuid) {
        var response = call(webClient.get()
                .uri(uri -> uri.path("/jobs/{id}/result").queryParam("user_uuid", userUuid).build(jobId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, EmbeddingServiceClient::toException)
                .bodyToMono(EmbeddingServiceResponse.class), "result " + jobId);

        return response != null && response.documents() != null
                ? response.documents()
                : List.of();
    }

    public void cancel(String jobId, String userUuid) {
        call(webClient.delete()
                .uri(uri -> uri.path("/jobs/{id}").queryParam("user_uuid", userUuid).build(jobId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, EmbeddingServiceClient::toException)
                .toBodilessEntity(), "cancel " + jobId);
    }

    /**
     * Annulation best-effort : ne lève jamais d'exception (suppression d'un document, timeout d'un job).
     */
    public void cancelQuietly(String jobId, String userUuid) {
        try {
            cancel(jobId, userUuid);
            log.info("Job d'embedding {} annulé", jobId);
        } catch (Exception e) {
            log.warn("Impossible d'annuler le job d'embedding {} : {}", jobId, e.getMessage());
        }
    }

    private <T> T call(Mono<T> mono, String operation) {
        try {
            return mono.timeout(requestTimeout).block();
        } catch (EmbeddingServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingServiceException("Erreur d'appel au service d'embedding (" + operation + ")", e);
        }
    }

    private static Mono<? extends Throwable> toException(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new EmbeddingServiceException(status, extractCode(body),
                        "Service d'embedding : HTTP " + status + " " + body));
    }

    private static String extractCode(String body) {
        var matcher = ERROR_CODE_PATTERN.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
