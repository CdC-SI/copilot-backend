package zas.admin.zec.backend.actions.visualize;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.visualize.model.MedicalServices;
import zas.admin.zec.backend.actions.visualize.model.TextTranslation;
import zas.admin.zec.backend.actions.visualize.model.ZasDocumentType;
import zas.admin.zec.backend.actions.visualize.model.sumex.SumexInvoice;

import java.util.List;

@Service
public interface VisionService {

    JsonNode extractFieldsFromFile(MultipartFile file, List<String> fields);

    MedicalServices extractTariffPositionsFromFile(MultipartFile file);

    ZasDocumentType classifyFile(MultipartFile file);

    TextTranslation translateFile(MultipartFile file, String language);

    SumexInvoice extractSumexInvoiceFromFile(MultipartFile file);
}
