package zas.admin.zec.backend.actions.catalog;

import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.config.ExceptionsHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogControllerTest {

    private final CatalogService service = mock(CatalogService.class);
    private final CatalogYamlMapper mapper = new CatalogYamlMapper(Validation.buildDefaultValidatorFactory().getValidator());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CatalogController(service, mapper))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void import_validYaml_returnsReport() throws Exception {
        var report = new CatalogImportReport(
                new CatalogImportReport.ItemReport(List.of("rh"), List.of(), List.of()),
                new CatalogImportReport.ItemReport(List.of(), List.of(), List.of()));
        when(service.importCatalog(any())).thenReturn(report);

        mvc.perform(multipart("/api/admin/catalog/import").file(yaml("version: 1\nsources:\n  - name: rh\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.created[0]").value("rh"));
    }

    @Test
    void import_emptyFile_returns400WithoutImport() throws Exception {
        mvc.perform(multipart("/api/admin/catalog/import").file(yaml("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Fichier de catalogue invalide"))
                .andExpect(jsonPath("$.messages[0]").value("Le fichier est vide"));
        verify(service, never()).importCatalog(any());
    }

    @Test
    void import_fileOverOneMegabyte_returns400() throws Exception {
        var big = new MockMultipartFile("file", "catalog.yaml", "application/yaml", new byte[(int) CatalogController.MAX_FILE_SIZE + 1]);

        mvc.perform(multipart("/api/admin/catalog/import").file(big))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("1 Mo")));
        verify(service, never()).importCatalog(any());
    }

    @Test
    void import_malformedYaml_returns400WithParserMessage() throws Exception {
        mvc.perform(multipart("/api/admin/catalog/import").file(yaml("version: 1\nsources: [unclosed")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(startsWith("YAML invalide")));
    }

    @Test
    void export_returnsYamlAttachment() throws Exception {
        when(service.exportCatalog()).thenReturn(mapper.parse(new java.io.ByteArrayInputStream(
                "version: 1\nsources:\n  - name: rh\n".getBytes(StandardCharsets.UTF_8))));

        mvc.perform(get("/api/admin/catalog/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"catalog.yaml\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("application/yaml")))
                .andExpect(content().string(containsString("name: rh")));
    }

    private static MockMultipartFile yaml(String content) {
        return new MockMultipartFile("file", "catalog.yaml", "application/yaml", content.getBytes(StandardCharsets.UTF_8));
    }
}
