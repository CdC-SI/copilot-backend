package zas.admin.zec.backend.actions.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import zas.admin.zec.backend.config.properties.EmbeddingServiceProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceClientTest {

    private final List<ClientRequest> requests = new ArrayList<>();

    private EmbeddingServiceClient clientReturning(HttpStatus status, String body) {
        var builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        });
        var properties = new EmbeddingServiceProperties("http://service", Duration.ofSeconds(3),
                Duration.ofSeconds(30), Duration.ofMinutes(5), 10, 3, Duration.ofHours(6),
                Duration.ofSeconds(5), DataSize.ofMegabytes(64));
        return new EmbeddingServiceClient(builder, properties);
    }

    @Test
    @DisplayName("submit posts a multipart request to /jobs and returns the job id")
    void submit_postsMultipart() {
        var client = clientReturning(HttpStatus.ACCEPTED,
                "{\"job_id\":\"job-1\",\"status\":\"queued\",\"pages_total\":45,\"priority\":\"low\"}");

        var response = client.submit("%PDF".getBytes(), "user", "doc.pdf");

        assertEquals("job-1", response.jobId());
        assertEquals(45, response.pagesTotal());
        var request = requests.getFirst();
        assertEquals(HttpMethod.POST, request.method());
        assertEquals("/jobs", request.url().getPath());
        assertTrue(request.headers().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA));
    }

    @Test
    @DisplayName("status queries /jobs/{id} with user_uuid")
    void status_queriesJob() {
        var client = clientReturning(HttpStatus.OK,
                "{\"job_id\":\"job-1\",\"status\":\"completed_with_errors\",\"pages_total\":3,\"pages_done\":3,\"pages_failed\":1}");

        var response = client.status("job-1", "user");

        assertTrue(response.jobStatus().hasResult());
        assertEquals(1, response.pagesFailed());
        var request = requests.getFirst();
        assertEquals("/jobs/job-1", request.url().getPath());
        assertEquals("user_uuid=user", request.url().getQuery());
    }

    @Test
    @DisplayName("result returns the documents of the job")
    void result_returnsDocuments() {
        var client = clientReturning(HttpStatus.OK,
                "{\"documents\":[{\"content\":\"c\",\"embedding\":\"0.1,0.2\",\"metadata\":{\"title\":\"doc.pdf\"}}]}");

        var chunks = client.result("job-1", "user");

        assertEquals(1, chunks.size());
        assertEquals("c", chunks.getFirst().content());
        assertEquals("/jobs/job-1/result", requests.getFirst().url().getPath());
    }

    @Test
    @DisplayName("HTTP errors are translated with their code")
    void errors_areTranslated() {
        var client = clientReturning(HttpStatus.GONE, "{\"detail\":{\"code\":\"job_lost\",\"message\":\"restarted\"}}");

        var ex = assertThrows(EmbeddingServiceException.class, () -> client.status("job-1", "user"));

        assertEquals(410, ex.getHttpStatus());
        assertEquals(EmbeddingServiceException.CODE_JOB_LOST, ex.getCode());
        assertTrue(ex.isJobGone());
        assertFalse(ex.isPermanent());
    }

    @Test
    @DisplayName("cancelQuietly never throws")
    void cancelQuietly_neverThrows() {
        var client = clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        assertDoesNotThrow(() -> client.cancelQuietly("job-1", "user"));
        assertEquals(HttpMethod.DELETE, requests.getFirst().method());
    }
}
