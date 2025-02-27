package zas.admin.zec.backend.actions.parametrize;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import zas.admin.zec.backend.actions.authorize.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    @Qualifier("pyBackendClient")
    private final WebClient pyBackendClient;
    private final UserService userService;

    public SettingController(WebClient pyBackendClient, UserService userService) {
        this.pyBackendClient = pyBackendClient;
        this.userService = userService;
    }

    @GetMapping()
    public ResponseEntity<String[]> getSettings(
            @RequestParam SettingType type,
            Authentication authentication) {

        String uri = "/apy/v1/settings/" + type.getName();

        if ((type == SettingType.SOURCE || type == SettingType.TAG) && authentication != null) {
            String userUuid = userService.getUuid(authentication.getName());

            List<String> organizations = userService.getOrganizations(authentication.getName());

            StringBuilder params = new StringBuilder("?user_uuid=").append(userUuid);
            if (organizations != null && !organizations.isEmpty()) {
                params.append("&organizations=").append(String.join(",", organizations));
            }
            uri += params.toString();
        }

        String[] response = pyBackendClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String[].class)
                .block();

        return ResponseEntity.ok(response);
    }
}

