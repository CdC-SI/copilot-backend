package zas.admin.zec.backend.actions.convert;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zas.admin.zec.backend.actions.visualize.model.sumex.SumexInvoice;

import java.io.IOException;

@RestController
@RequestMapping("/api/sumex-invoices")
public class SumexInvoicesController {

    private final SumexInvoiceToXmlTemplateService templateService;

    public SumexInvoicesController(SumexInvoiceToXmlTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping("/convert")
    public ResponseEntity<Resource> convertSumexInvoice(@RequestBody SumexInvoice sumexInvoice) throws IOException {
        var xmlInvoice = templateService.render(sumexInvoice);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=%s".formatted(xmlInvoice.getFilename()))
                .contentType(MediaType.TEXT_XML)
                .body(xmlInvoice);
    }
}
