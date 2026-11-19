package zas.admin.zec.backend.actions.parametrize;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping()
    public ResponseEntity<List<String>> getSettings(@RequestParam SettingType type) {
        return ResponseEntity.ok(settingService.getSettings(type));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getTagsFilteredBySources(@RequestParam String sources) {
        List<String> sourceList = sources != null ? List.of(sources.split(",")) : List.of();
        return ResponseEntity.ok(settingService.getTags(sourceList));
    }
}