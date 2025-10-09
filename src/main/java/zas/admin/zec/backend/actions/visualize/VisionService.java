package zas.admin.zec.backend.actions.visualize;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public interface VisionService {

    JsonNode extractFieldsFromFile(MultipartFile file, List<String> fields);

    MedicalServices extractTariffPositionsFromFile(MultipartFile file);

    ZasDocumentType classifyFile(MultipartFile file);

    TextTranslation translateFile(MultipartFile file, String language);
}
