package zas.admin.zec.backend.conversations;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.users.UserService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    public record Question(String query) {}
    @Qualifier("pyBackendWebClient")
    private final WebClient pyBackendWebClient;
    private final UserService userService;

    public ConversationController(WebClient pyBackendWebClient, UserService userService) {
        this.pyBackendWebClient = pyBackendWebClient;
        this.userService = userService;
    }

    @GetMapping("/titles")
    public ResponseEntity<List<ConversationTitle>> getConversationTitles(Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        List<ConversationTitle> titles = pyBackendWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/apy/conversations/titles")
                        .queryParam("user_uuid", userUuid)
                        .build())
                .retrieve()
                .bodyToFlux(ConversationTitle.class)
                .filter(title -> title.userId().equals(userUuid))
                .collectList()
                .block();

        return ResponseEntity.ok(titles);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<List<Message>> getConversation(@PathVariable String conversationId, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        List<Message> messages = pyBackendWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/apy/conversations/")
                        .path(conversationId)
                        .build())
                .retrieve()
                .bodyToFlux(Message.class)
                .filter(message -> message.userId().equals(userUuid))
                .collectList()
                .block();

        return ResponseEntity.ok(messages);
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
