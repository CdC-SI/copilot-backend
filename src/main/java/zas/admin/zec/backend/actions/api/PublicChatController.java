package zas.admin.zec.backend.actions.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import zas.admin.zec.backend.agent.RAGAgent;
import zas.admin.zec.backend.config.api.RequireExternalClient;

import java.util.Map;

import static zas.admin.zec.backend.actions.api.StreamEventType.COMPLETED;
import static zas.admin.zec.backend.actions.api.StreamEventType.ERROR;

@Slf4j
@RestController
@RequestMapping("/api/public/v1")
public class PublicChatController {

    private final RAGAgent ragAgent;
    private final ObjectMapper mapper;

    public PublicChatController(RAGAgent ragAgent, ObjectMapper mapper) {
        this.ragAgent = ragAgent;
        this.mapper = mapper;
    }

    @RequireExternalClient
    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest request) {
        return ragAgent
                .processPublicQuestion(request.input())
                .map(evt -> ServerSentEvent.<String>builder()
                        .event(evt.type().getEventName())
                        .data(toJson(evt.payload()))
                        .build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder()
                        .event(COMPLETED.getEventName())
                        .data("{}")
                        .build()))
                .doOnSubscribe(s -> log.info("SSE start: {}", request.input()))
                .onErrorResume(ex -> {
                    log.warn("Streaming error: {}", ex.toString());
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event(ERROR.getEventName())
                                    .data(toJson(Map.of("message", ex.getMessage() == null ? "Internal error" : ex.getMessage())))
                                    .build(),
                            ServerSentEvent.<String>builder()
                                    .event(COMPLETED.getEventName())
                                    .data("{}")
                                    .build()
                    );
                });
    }

    private String toJson(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Ne jamais casser le stream pour un souci de sérialisation: on fallback en texte simple
            return "{\"_unserializable\":true}";
        }
    }
}
