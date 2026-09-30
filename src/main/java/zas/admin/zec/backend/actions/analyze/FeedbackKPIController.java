package zas.admin.zec.backend.actions.analyze;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO.MessageFeedback;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO.SourceFeedback;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO.Stats;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO.StatusUpdateRequest;
import zas.admin.zec.backend.actions.analyze.model.FeedbackReportConfig;
import zas.admin.zec.backend.config.security.RequireAdmin;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;
import zas.admin.zec.backend.persistence.entity.FeedbackStatus;

import java.util.List;

@RequireAdmin
@RestController
@RequestMapping("/api/feedback")
public class FeedbackKPIController {

    private final FeedbackQueryService svc;
    private final FeedbackReportConfigService reportConfigService;

    public FeedbackKPIController(FeedbackQueryService svc, FeedbackReportConfigService reportConfigService) {
        this.svc = svc;
        this.reportConfigService = reportConfigService;
    }

    @GetMapping("/stats")
    public Stats stats(@RequestParam(value = "range", required = false) String range) {
        return svc.stats(range);
    }

    @GetMapping("/messages")
    public List<MessageFeedback> listMessages(@RequestParam(value = "range", required = false) String range,
                                              @RequestParam(value = "includeDetails", defaultValue = "false") boolean includeDetails,
                                              @RequestParam(value = "status", required = false) FeedbackStatus status,
                                              @RequestParam(value = "category", required = false) FeedbackCategory category) {

        return svc.listMessages(range, includeDetails, status, category);
    }

    @GetMapping("/sources")
    public List<SourceFeedback> listSources(@RequestParam(value = "range", required = false) String range,
                                             @RequestParam(value = "status", required = false) FeedbackStatus status,
                                             @RequestParam(value = "category", required = false) FeedbackCategory category) {
        return svc.listSources(range, status, category);
    }

    @PatchMapping("/messages/{id}/status")
    public MessageFeedback updateMessageStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        return svc.updateMessageFeedbackStatus(id, request.status());
    }

    @PatchMapping("/sources/{id}/status")
    public SourceFeedback updateSourceStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        return svc.updateSourceFeedbackStatus(id, request.status());
    }

    @GetMapping("/report-config")
    public FeedbackReportConfig getReportConfig() {
        return reportConfigService.get();
    }

    @PutMapping("/report-config")
    public FeedbackReportConfig updateReportConfig(@Valid @RequestBody FeedbackReportConfig config) {
        return reportConfigService.update(config);
    }
}


