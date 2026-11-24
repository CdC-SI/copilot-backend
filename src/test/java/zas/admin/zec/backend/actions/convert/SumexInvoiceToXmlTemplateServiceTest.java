package zas.admin.zec.backend.actions.convert;

import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;
import zas.admin.zec.backend.actions.visualize.model.sumex.InvoiceAuthor;
import zas.admin.zec.backend.actions.visualize.model.sumex.InvoiceMetaData;
import zas.admin.zec.backend.actions.visualize.model.sumex.MedicalService;
import zas.admin.zec.backend.actions.visualize.model.sumex.Patient;
import zas.admin.zec.backend.actions.visualize.model.sumex.PaymentInformation;
import zas.admin.zec.backend.actions.visualize.model.sumex.SumexInvoice;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu XML Sumex avec le vrai gabarit FreeMarker (configuration construite comme en production).
 */
class SumexInvoiceToXmlTemplateServiceTest {

    private SumexInvoiceToXmlTemplateService service;

    @BeforeEach
    void setUp() throws Exception {
        var factory = new FreeMarkerConfigurationFactoryBean();
        factory.setTemplateLoaderPath("classpath:/templates/");
        factory.setPreferFileSystemAccess(false);
        factory.setDefaultEncoding("UTF-8");
        factory.afterPropertiesSet();
        Configuration freemarker = factory.getObject();
        service = new SumexInvoiceToXmlTemplateService(freemarker);
    }

    @Test
    void render_fillsInvoiceAndUsesInvoiceNumberAsFileName() throws Exception {
        var resource = service.render(invoice("F-2024-01", "female", "1990-05-01"));

        var xml = resource.getContentAsString(StandardCharsets.UTF_8);
        assertThat(resource.getFilename()).isEqualTo("F-2024-01.xml");
        assertThat(xml)
                .contains("from=\"7601000000001\"")
                .contains("request_date=\"2024-03-15T00:00:00\"")
                .contains("request_id=\"F-2024-01\"")
                .contains("salutation=\"Frau\"")
                .contains("<invoice:familyname>Dupont</invoice:familyname>")
                .contains("birthdate=\"1990-05-01\"")
                .contains("record_id=\"1\"")
                .contains("code=\"00.0010\"")
                .contains("amount=\"120.5\"")
                .contains("iban=\"CH9300762011623852957\"");
    }

    @Test
    void render_blankValuesFallBackToTemplateDefaults() throws Exception {
        var resource = service.render(invoice("", "", ""));

        var xml = resource.getContentAsString(StandardCharsets.UTF_8);
        assertThat(resource.getFilename()).isEqualTo("sumex-invoice.xml");
        assertThat(xml)
                .contains("request_id=\"1\"")
                .contains("salutation=\"Unbekannt\"")
                .contains("birthdate=\"1990-12-20\"");
    }

    @Test
    void render_missingPatient_usesTemplateDefaults() throws Exception {
        var xml = service.render(new SumexInvoice(author(), null, metaData("F1"), List.of(), payment(), 0.0))
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(xml)
                .contains("gender=\"male\"", "birthdate=\"1990-12-20\"", "ssn=\"7560665324444\"")
                .contains("salutation=\"Unbekannt\"")
                .contains("<invoice:familyname></invoice:familyname>")
                .contains("case_id=\"312280\"");
    }

    @Test
    void render_patientWithNullFields_usesTemplateDefaults() throws Exception {
        var patient = new Patient("Dupont", null, null, null, null, null, null, null, null, null, null, null, null);

        var xml = service.render(new SumexInvoice(author(), patient, metaData("F2"), List.of(), payment(), 0.0))
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(xml)
                .contains("<invoice:familyname>Dupont</invoice:familyname>", "<invoice:givenname></invoice:givenname>")
                .contains("gender=\"male\"", "salutation=\"Unbekannt\"", "case_date=\"2025-01-01\"");
    }

    @Test
    void render_templateError_isWrappedInIOException() {
        // Auteur absent : pas de valeur par défaut dans le gabarit.
        var incomplete = new SumexInvoice(null, null, metaData("F1"), List.of(), payment(), 0.0);

        assertThatThrownBy(() -> service.render(incomplete))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("sumex_invoice_xml.ftl");
    }

    @Test
    void controller_returnsXmlAttachment() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SumexInvoicesController(service)).build();
        var body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(invoice("F9", "male", ""));

        mvc.perform(post("/api/sumex-invoices/convert").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=F9.xml"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                .andExpect(content().string(containsString("salutation=\"Herr\"")));
    }

    private static SumexInvoice invoice(String number, String gender, String birthday) {
        var patient = new Patient("Dupont", "Jeanne", "Rue 1", "1950", "Sion", "", "CH", birthday, gender,
                "", "", "", "");
        var service = new MedicalService("2024-03-01", "001", "00.0010", "Consultation", 1.0, 120.5);
        return new SumexInvoice(author(), patient, metaData(number), List.of(service), payment(), 120.5);
    }

    private static InvoiceAuthor author() {
        return new InvoiceAuthor("7601000000001", "Cabinet", "Sion");
    }

    private static InvoiceMetaData metaData(String number) {
        return new InvoiceMetaData("invoice", "2024-03-01", "2024-03-01", "disease", LocalDate.of(2024, 3, 15), number);
    }

    private static PaymentInformation payment() {
        return new PaymentInformation("CHF", "QR", "CH9300762011623852957", "", "", "Cabinet", "Rue 2", "CH",
                "1950", "Sion", "", "");
    }
}
