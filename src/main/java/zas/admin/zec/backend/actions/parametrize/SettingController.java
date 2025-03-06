package zas.admin.zec.backend.actions.parametrize;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping()
    public ResponseEntity<List<String>> getSettings(@RequestParam SettingType type, Authentication authentication) {
        return Objects.isNull(authentication)
                ? ResponseEntity.ok(settingService.getPublicSettings(type))
                : ResponseEntity.ok(settingService.getSettings(type, authentication.getName()));
    }
}