package zas.admin.zec.backend.actions.parametrize;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zas.admin.zec.backend.actions.authorize.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingService settingService;
    private final UserService userService;

    public SettingController(SettingService settingService, UserService userService) {
        this.settingService = settingService;
        this.userService = userService;
    }

    @GetMapping()
    public ResponseEntity<List<String>> getSettings(@RequestParam SettingType type, Authentication authentication) {
        return userService.existsByUsername(authentication.getName())
                ? ResponseEntity.ok(settingService.getSettings(type, authentication.getName()))
                : ResponseEntity.ok(settingService.getPublicSettings(type));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getTagsFilteredBySources(@RequestParam String sources, Authentication authentication) {
        List<String> sourceList = sources != null ? List.of(sources.split(",")) : List.of();
        return userService.existsByUsername(authentication.getName())
                ? ResponseEntity.ok(settingService.getTags(authentication.getName(), sourceList))
                : ResponseEntity.ok(settingService.getPublicTags(sourceList));
    }
}