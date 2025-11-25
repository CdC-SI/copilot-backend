package zas.admin.zec.backend.actions.convert;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.actions.visualize.model.sumex.SumexInvoice;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class SumexInvoiceToXmlTemplateService {

    private static final String SUMEX_XML_TEMPLATE = "sumex_invoice_xml.ftl";
    private final Configuration fm;

    public SumexInvoiceToXmlTemplateService(Configuration fm) {
        this.fm = fm;
    }

    public Resource render(SumexInvoice model) throws IOException {
        Template tpl = fm.getTemplate(SUMEX_XML_TEMPLATE, StandardCharsets.UTF_8.name());
        var date = model.metaData().creationDate().atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME).toString();
        Map<String, Object> dataModel = new HashMap<>();
        //Root
        dataModel.put("invoice", model);
        // Expose all fields, optional but convenient for template
        dataModel.put("author", model.author());
        dataModel.put("patient", model.patient());
        dataModel.put("metadata", model.metaData());
        dataModel.put("medicalServices", model.medicalServices());
        dataModel.put("paymentInformation", model.paymentInformation());
        dataModel.put("totalAmount", model.totalAmount());
        dataModel.put("creationDate", date);

        try (StringWriter out = new StringWriter()) {
            tpl.process(dataModel, out);

            byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);

            // Optionally give the Resource a filename based on invoiceNumber
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return model.metaData() != null
                            ? model.metaData().invoiceNumber() + ".xml"
                            : "sumex-invoice.xml";
                }
            };
        } catch (TemplateException e) {
            throw new IOException("Error while processing FreeMarker template " + SUMEX_XML_TEMPLATE, e);
        }
    }
}
