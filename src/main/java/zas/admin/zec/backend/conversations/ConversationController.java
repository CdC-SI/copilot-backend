package zas.admin.zec.backend.conversations;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    public record Question(String query) {}
    @Qualifier("pyBackendWebClient")
    private final WebClient pyBackendWebClient;

    public ConversationController(WebClient pyBackendWebClient) {
        this.pyBackendWebClient = pyBackendWebClient;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askQuestion(@RequestBody Question question) {
        return pyBackendWebClient.post()
                .uri("/apy/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(question)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchangeToFlux(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(DataBuffer.class)
                                .map(dataBuffer -> {
                                    String chunk = dataBuffer.toString(StandardCharsets.UTF_8);
                                    DataBufferUtils.release(dataBuffer);
                                    return chunk;
                                });
                    } else {
                        return response.createException().flatMapMany(Flux::error);
                    }
                });
    }
}
