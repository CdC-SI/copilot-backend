package zas.admin.zec.backend.conversations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.users.UserService;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    @Qualifier("pyBackendWebClient")
    private final WebClient pyBackendWebClient;
    private final UserService userService;
    private final ConversationService conversationService;

    public ConversationController(WebClient pyBackendWebClient, UserService userService, ConversationService conversationService) {
        this.pyBackendWebClient = pyBackendWebClient;
        this.userService = userService;
        this.conversationService = conversationService;
    }

    @GetMapping("/titles")
    public ResponseEntity<List<ConversationTitle>> getConversationTitles(Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        List<ConversationTitle> titles = pyBackendWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/apy/v1/conversations/titles")
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
                .uri(uriBuilder -> uriBuilder.path("/apy/v1/conversations/")
                        .path(conversationId)
                        .build())
                .retrieve()
                .bodyToFlux(Message.class)
                .filter(message -> message.userId().equals(userUuid))
                .collectList()
                .block();

        return ResponseEntity.ok(messages);
    }

    @PostMapping("/init")
    public ResponseEntity<Void> initConversation(@RequestBody List<Message> messages, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        var conversationId = UUID.randomUUID().toString();
        conversationService.initConversation(userUuid, conversationId, messages);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{conversationId}")
    public ResponseEntity<Void> updateConversation(@PathVariable String conversationId, @RequestBody List<Message> messages, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        conversationService.update(userUuid, conversationId, messages);

        return ResponseEntity.ok().build();
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askQuestion(@RequestBody Question question, Authentication authentication) {
        var userUuid = authentication != null
                ? userService.getUuid(authentication.getName())
                : null;

        return pyBackendWebClient.post()
                .uri("/apy/v1/chat/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(questionToChatRequest(question, userUuid))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchangeToFlux(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(DataBuffer.class)
                                .map(dataBuffer -> {
                                    String chunk = dataBuffer.toString(StandardCharsets.UTF_8);
                                    log.info("Received chunk: {}", chunk);
                                    DataBufferUtils.release(dataBuffer);
                                    return chunk;
                                });
                    } else {
                        return response.createException().flatMapMany(Flux::error);
                    }
                });
    }

    @PostMapping("/feedbacks")
    public ResponseEntity<Void> sendFeedback(@RequestBody Feedback feedback, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        pyBackendWebClient.put()
                .uri(uriBuilder -> uriBuilder.path(feedback.isPositive()
                                ? "/apy/v1/conversations/feedback/thumbs_up"
                                : "/apy/v1/conversations/feedback/thumbs_down")
                        .queryParam("user_uuid", userUuid)
                        .queryParam("conversation_uuid", feedback.conversationId())
                        .queryParam("message_uuid", feedback.messageId())
                        .queryParam("comment", feedback.comment())
                        .build())
                .retrieve()
                .toBodilessEntity()
                .block();

        return ResponseEntity.ok().build();
    }

    private Map<String, Object> questionToChatRequest(Question question, String userUuid) {
        Map<String, Object> chatRequest = new HashMap<>();
        chatRequest.put("query", question.query());
        chatRequest.put("autocomplete", question.autocomplete());
        chatRequest.put("rag", question.rag());
        chatRequest.put("language", question.language());
        addEntryIfValueNotNull(chatRequest, "llm_model", question.llmModel());
        addEntryIfValueNotNull(chatRequest, "temperature", question.temperature());
        addEntryIfValueNotNull(chatRequest, "top_p", question.topP());
        addEntryIfValueNotNull(chatRequest, "max_output_tokens", question.maxOutputTokens());
        addEntryIfValueNotNull(chatRequest, "response_style", question.responseStyle());
        addEntryIfValueNotNull(chatRequest, "user_uuid", userUuid);
        addEntryIfValueNotNull(chatRequest, "k_memory", question.kMemory());
        addEntryIfValueNotNull(chatRequest, "tags", question.tags());
        addEntryIfValueNotNull(chatRequest, "source", question.sources());
        addEntryIfValueNotNull(chatRequest, "retrieval_method", question.retrievalMethods());
        addEntryIfValueNotNull(chatRequest, "k_retrieve", question.kRetrieve());
        addEntryIfValueNotNull(chatRequest, "command", question.command());
        addEntryIfValueNotNull(chatRequest, "command_args", question.commandArgs());
        addEntryIfValueNotNull(chatRequest, "agentic_rag", question.agenticRag());
        addEntryIfValueNotNull(chatRequest, "is_followup_q", question.isFollowUpQ());

        if (userUuid != null && question.conversationId() == null) {
            chatRequest.put("conversation_uuid", UUID.randomUUID().toString());
        } else {
            addEntryIfValueNotNull(chatRequest, "conversation_uuid", question.conversationId());
        }

        return chatRequest;
    }

    private void addEntryIfValueNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
