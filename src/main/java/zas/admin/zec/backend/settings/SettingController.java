package zas.admin.zec.backend.settings;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    @Qualifier("pyBackendClient")
    private final WebClient pyBackendClient;

    public SettingController(WebClient pyBackendClient) {
        this.pyBackendClient = pyBackendClient;
    }

    @GetMapping()
    public ResponseEntity<String[]> getSettings(@RequestParam SettingType type) {
        return ResponseEntity.ok(pyBackendClient.get()
                .uri("/apy/options/" + type.getName())
                .retrieve()
                .bodyToMono(String[].class)
                .block());
    }
}

