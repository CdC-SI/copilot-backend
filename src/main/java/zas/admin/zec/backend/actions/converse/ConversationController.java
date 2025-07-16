package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.RequireUser;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequireUser
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final UserService userService;
    private final ConversationService conversationService;

    public ConversationController(UserService userService, ConversationService conversationService) {
        this.userService = userService;
        this.conversationService = conversationService;
    }

    @GetMapping("/titles")
    public ResponseEntity<List<ConversationTitle>> getConversationTitles(Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        var titles = conversationService.getTitlesByUserId(userUuid);
        return ResponseEntity.ok(titles);
    }

    @PutMapping("/titles/{conversationId}")
    public ResponseEntity<Void> updateConversationTitle(@PathVariable String conversationId, @RequestBody ConversationTitleUpdate titleUpdate, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        conversationService.renameConversation(userUuid, conversationId, titleUpdate.newTitle());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<List<Message>> getConversation(@PathVariable String conversationId, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        var conversation = conversationService.getByConversationIdAndUserId(conversationId, userUuid);
        return ResponseEntity.ok(conversation);
    }

    @PostMapping("/init")
    public ResponseEntity<Void> initConversation(@RequestBody List<FAQMessage> messages, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        var conversationId = UUID.randomUUID().toString();
        conversationService.initConversation(userUuid, conversationId, messages);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{conversationId}")
    public ResponseEntity<Void> updateConversation(@PathVariable String conversationId, @RequestBody List<FAQMessage> messages, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        conversationService.update(userUuid, conversationId, messages);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        conversationService.delete(userUuid, conversationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askQuestion(@RequestBody Question question, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        return conversationService.streamAnswer(question.withDefaults(), userUuid);
    }
}
