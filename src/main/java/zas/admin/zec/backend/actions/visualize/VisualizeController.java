package zas.admin.zec.backend.actions.visualize;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/visualize")
public class VisualizeController {
    private final VisionService visionService;

    public VisualizeController(VisionService visionService) {
        this.visionService = visionService;
    }

    @PostMapping("/structure")
    public ResponseEntity<JsonNode> structureFile(@ModelAttribute StructureDataRequest structureDataRequest) {
        var result = visionService.extractFieldsFromFile(structureDataRequest.file(), structureDataRequest.fields());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/classify")
    public ResponseEntity<ZasDocumentType> classifyFile(@RequestParam MultipartFile file) {
        var classification = visionService.classifyFile(file);
        return ResponseEntity.ok(classification);
    }

    @PostMapping("/translate")
    public ResponseEntity<TextTranslation> translateFile(@RequestParam MultipartFile file, @RequestParam String language) {
        var translation = visionService.translateFile(file, language);
        return ResponseEntity.ok(translation);
    }
}
