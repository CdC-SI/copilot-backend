package zas.admin.zec.backend.actions.askfaq;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.config.security.RequireExpert;

import java.util.List;

@RestController
@RequestMapping("/api/faq-items")
public class FAQItemsController {
    private final FAQService faqService;

    public FAQItemsController(FAQService faqService) {
        this.faqService = faqService;
    }

    @GetMapping
    public ResponseEntity<List<FAQItem>> searchFAQItems(@RequestParam String question) {
        return ResponseEntity.ok(faqService.getExistingFAQItemsByMatchingQuestion(question));
    }

    @PostMapping
    @RequireExpert
    public ResponseEntity<FAQItem> saveFAQItem(@RequestBody FAQItemLight faqItem) {
        return ResponseEntity.ok(faqService.save(faqItem));
    }
}
