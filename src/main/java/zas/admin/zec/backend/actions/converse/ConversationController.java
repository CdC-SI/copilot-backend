package zas.admin.zec.backend.actions.converse;

import ch.admin.zas.jweb.securityevents.core.utils.OPDOOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.security.RequireUser;
import zas.admin.zec.backend.tools.SecurityLogging;
import zas.admin.zec.backend.tools.OpdoPersonalData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequireUser
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final UserService userService;
    private final ConversationService conversationService;
    private final SecurityLogging securityLogging;

    public ConversationController(UserService userService, ConversationService conversationService, SecurityLogging securityLogging) {
        this.userService = userService;
        this.conversationService = conversationService;
        this.securityLogging = securityLogging;
    }

    @GetMapping("/workspaces")
    public ResponseEntity<List<String>> getWorkspaces() {
        return ResponseEntity.ok(conversationService.getWorkspaces());
    }

    @GetMapping("/titles")
    public ResponseEntity<List<ConversationTitle>> getConversationTitles(Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        var user = userService.getByUsername(authentication.getName());
        var titles = conversationService.getTitlesByUserId(userUuid);
        securityLogging.log("Accès aux conversations de l'utilisateur", OPDOOperation.READING,
                List.of(OpdoPersonalData.builder().field("firstName", user.firstName()).field("lastName", user.lastName()).build()));
        return ResponseEntity.ok(titles);
    }

    @PutMapping("/titles/{conversationId}")
    public ResponseEntity<Void> updateConversationTitle(@PathVariable String conversationId, @RequestBody ConversationTitleUpdate titleUpdate, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        conversationService.renameConversation(userUuid, conversationId, titleUpdate.newTitle());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<Conversation> getConversation(@PathVariable String conversationId, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        var user = userService.getByUsername(authentication.getName());
        var conversation = conversationService.getByConversationIdAndUserId(conversationId, userUuid);
        securityLogging.log("Accès au contenu d'une conversation", OPDOOperation.READING,
                List.of(OpdoPersonalData.builder().field("firstName", user.firstName()).field("lastName", user.lastName()).build()));
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
        var user = userService.getByUsername(authentication.getName());
        conversationService.delete(userUuid, conversationId);
        securityLogging.log("Suppression d'une conversation", OPDOOperation.DELETION,
                List.of(OpdoPersonalData.builder().field("firstName", user.firstName()).field("lastName", user.lastName()).build()));
        return ResponseEntity.ok().build();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askQuestion(@ModelAttribute Question question, Authentication authentication) {
        var userUuid = "d0c7a301-53cc-4292-9c9c-24bfbee0907a";
        return conversationService.streamAnswer(question.withDefaults(), userUuid);
    }

    @GetMapping("/{conversationId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> getAttachment(@PathVariable String conversationId, @PathVariable Long attachmentId, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        var attachment = conversationService.getAttachment(conversationId, attachmentId, userUuid);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(attachment.filename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(attachment.content());
    }

    @PostMapping(path = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentUploadResponse> uploadAttachment(@RequestParam(required = false) String conversationId, List<MultipartFile> files, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        try {
            var attachmentIdsByConvId = conversationService.attachFilesToConversation(conversationId, userUuid, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(AttachmentUploadResponse.success(attachmentIdsByConvId));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AttachmentUploadResponse.failure("Failed to process attachments"));
        }
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        conversationService.deleteAttachment(attachmentId, userUuid);
        return ResponseEntity.ok().build();
    }
}
