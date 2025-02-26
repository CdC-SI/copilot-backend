package zas.admin.zec.backend.actions.ask;

import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/faq-items")
public class FAQItemsController {

    public record FAQItem(@Nullable Long id, String text, String answer, String url, String language) {}
    @Qualifier("pyBackendWebClient")
    private final WebClient pyBackendWebClient;

    public FAQItemsController(WebClient pyBackendWebClient) {
        this.pyBackendWebClient = pyBackendWebClient;
    }

    @GetMapping
    public ResponseEntity<Object> searchFAQItems(@RequestParam String question) {
        return pyBackendWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/apy/v1/autocomplete/")
                        .queryParam("question", question)
                        .build())
                .retrieve()
                .toEntity(Object.class)
                .block();
    }

    @PutMapping
    public ResponseEntity<Object> updateFAQItem(@RequestBody FAQItem faqItem) {
        return pyBackendWebClient.put()
                .uri(uriBuilder -> uriBuilder.path("/apy/v1/indexing/data")
                        .build())
                .bodyValue(faqItem)
                .retrieve()
                .toEntity(Object.class)
                .block();
    }
}
