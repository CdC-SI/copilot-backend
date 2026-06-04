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
import zas.admin.zec.backend.config.api.RequireExternalClient;

import java.util.Map;

import static zas.admin.zec.backend.actions.api.StreamEventType.COMPLETED;
import static zas.admin.zec.backend.actions.api.StreamEventType.DELTA;

@Slf4j
@RestController
@RequestMapping("/api/public/v1")
public class PublicChatController {

    private final ObjectMapper mapper;

    public PublicChatController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @RequireExternalClient
    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest request) {
        // TODO: l'API publique est temporairement débranchée (RAGAgent supprimé au profit de RAGTool).
        // Réponse factice pour les tests/validation tant que le ChatClient agentique public n'est pas câblé.
        return Flux.just(
                        ServerSentEvent.<String>builder()
                                .event(DELTA.getEventName())
                                .data(toJson(Map.of("delta", "Public API temporairement indisponible.")))
                                .build(),
                        ServerSentEvent.<String>builder()
                                .event(COMPLETED.getEventName())
                                .data("{}")
                                .build())
                .doOnSubscribe(s -> log.info("SSE start (dummy): {}", request.input()));
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
