package zas.admin.zec.backend.faqs;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/faqs")
public class FAQController {

    private final WebClient pyBackendWebClient;

    public FAQController(WebClient pyBackendWebClient) {
        this.pyBackendWebClient = pyBackendWebClient;
    }

    @GetMapping
    public ResponseEntity<Object> searchFAQItems(@RequestParam String question) {
        return pyBackendWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/apy/autocomplete/")
                        .queryParam("question", question)
                        .build())
                .retrieve()
                .toEntity(Object.class)
                .block();
    }
}
