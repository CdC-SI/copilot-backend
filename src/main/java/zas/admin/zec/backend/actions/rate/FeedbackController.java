package zas.admin.zec.backend.actions.rate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.security.RequireExpert;

import java.util.List;

@RestController
@RequestMapping("/api/conversations/feedbacks")
public class FeedbackController {

    private final UserService userService;
    private final FeedbackService feedbackService;

    public FeedbackController(UserService userService, FeedbackService feedbackService) {
        this.userService = userService;
        this.feedbackService = feedbackService;
    }

    @PostMapping(params = "type=answer")
    public ResponseEntity<Void> sendFeedback(@RequestBody Feedback feedback, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        feedbackService.sendFeedback(userUuid, feedback);
        return ResponseEntity.ok().build();
    }

    @RequireExpert
    @GetMapping(params = {"type=source", "conversationId", "messageId"})
    public ResponseEntity<List<SourceFeedback>> getFeedbacks(
            @RequestParam String conversationId,
            @RequestParam String messageId,
            Authentication authentication) {

        var userUuid = userService.getUuid(authentication.getName());
        var feedbacks = feedbackService.getFeedbacks(userUuid, conversationId, messageId);
        return ResponseEntity.ok(feedbacks);
    }

    @RequireExpert
    @PostMapping(params = "type=source")
    public ResponseEntity<Void> sendFeedback(@RequestBody SourceFeedback feedback, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        feedbackService.sendFeedback(userUuid, feedback);
        return ResponseEntity.ok().build();
    }
}
