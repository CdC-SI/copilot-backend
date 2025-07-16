package zas.admin.zec.backend.actions.rate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zas.admin.zec.backend.actions.authorize.UserService;

@RestController
@RequestMapping("/api/conversations/feedbacks")
public class FeedbackController {

    private final UserService userService;
    private final FeedbackService feedbackService;

    public FeedbackController(UserService userService, FeedbackService feedbackService) {
        this.userService = userService;
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<Void> sendFeedback(@RequestBody Feedback feedback, Authentication authentication) {
        var userUuid = userService.getUuid(authentication.getName());
        feedbackService.sendFeedback(userUuid, feedback);
        return ResponseEntity.ok().build();
    }
}
