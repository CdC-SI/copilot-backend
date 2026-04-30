package zas.admin.zec.backend.actions.visualize;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.visualize.model.TextTranslation;
import zas.admin.zec.backend.actions.visualize.model.ZasDocumentType;
import zas.admin.zec.backend.actions.visualize.model.sumex.SumexInvoice;
import zas.admin.zec.backend.config.security.RequireAdmin;
import zas.admin.zec.backend.config.security.RequireTranslator;

import java.util.List;

@RestController
@RequestMapping("/api/visualize")
public class VisualizeController {
    private final VisionService visionService;

    public VisualizeController(VisionService visionService) {
        this.visionService = visionService;
    }

    @RequireAdmin
    @PostMapping("/structure")
    public ResponseEntity<JsonNode> structureFile(@ModelAttribute StructureDataRequest structureDataRequest) {
        var result = visionService.extractFieldsFromFile(structureDataRequest.file(), structureDataRequest.fields());
        return ResponseEntity.ok(result);
    }

    @RequireAdmin
    @PostMapping("/classify")
    public ResponseEntity<ZasDocumentType> classifyFile(@RequestParam MultipartFile file) {
        var classification = visionService.classifyFile(file);
        return ResponseEntity.ok(classification);
    }

    @RequireTranslator
    @PostMapping("/translate")
    public ResponseEntity<List<TextTranslation>> translateFile(@RequestParam MultipartFile file, @RequestParam String language) {
        var translation = visionService.translateFile(file, language);
        return ResponseEntity.ok(translation);
    }

    @PostMapping("/sumex")
    public ResponseEntity<SumexInvoice> sumex(@RequestParam MultipartFile file) {
        var translation = visionService.extractSumexInvoiceFromFile(file);
        return ResponseEntity.ok(translation);
    }
}
