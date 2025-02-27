package zas.admin.zec.backend.actions.askfaq;

import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/faq-items")
public class FAQItemsController {

    public record FAQItemLight(@Nullable Long id, String text, String answer, String url, String language) {}
    @Qualifier("pyBackendWebClient")
    private final WebClient pyBackendWebClient;
    private final FAQService faqService;

    public FAQItemsController(WebClient pyBackendWebClient, FAQService faqService) {
        this.pyBackendWebClient = pyBackendWebClient;
        this.faqService = faqService;
    }

    @GetMapping
    public ResponseEntity<Object> searchFAQItems(@RequestParam String question) {
        return ResponseEntity.ok(faqService.getExistingFAQItemsByMatchingQuestion(question));
    }

    @PutMapping("/update")
    public ResponseEntity<Object> updateFAQItem(@RequestBody FAQItemLight faqItem) {
        return pyBackendWebClient.put()
                .uri(uriBuilder -> uriBuilder.path("/apy/v1/indexing/data")
                        .build())
                .bodyValue(faqItem)
                .retrieve()
                .toEntity(Object.class)
                .block();
    }
}
