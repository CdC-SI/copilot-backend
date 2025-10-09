package zas.admin.zec.backend.actions.analyze;

import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO.MessageFeedback;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO.SourceFeedback;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO.Stats;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackKPIController {

    private final FeedbackQueryService svc;
    public FeedbackKPIController(FeedbackQueryService svc) { this.svc = svc; }

    @GetMapping("/stats")
    public Stats stats(@RequestParam(value = "range", required = false) String range) {
        return svc.stats(range);
    }

    @GetMapping("/messages")
    public List<MessageFeedback> listMessages(@RequestParam(value = "range", required = false) String range,
                                              @RequestParam(value = "includeDetails", defaultValue = "false") boolean includeDetails) {

        return svc.listMessages(range, includeDetails);
    }


    @GetMapping("/messages/{id}")
    public Optional<MessageFeedback> getMessage(@PathVariable Integer id,
                                                @RequestParam(value = "includeDetails", defaultValue = "true") boolean includeDetails) {

        return svc.getMessage(id, includeDetails);
    }


    @GetMapping("/sources")
    public List<SourceFeedback> listSources(@RequestParam(value = "range", required = false) String range) {
        return svc.listSources(range);
    }
}
