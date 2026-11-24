package zas.admin.zec.backend.actions.visualize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.actions.visualize.model.TextTranslation;
import zas.admin.zec.backend.actions.visualize.model.ZasDocumentType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VisualizeControllerTest {

    private final VisionService visionService = mock(VisionService.class);
    private final MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new VisualizeController(visionService)).build();
    }

    @Test
    void structure_bindsFileAndFields() throws Exception {
        when(visionService.extractFieldsFromFile(any(), eq(List.of("name", "navs"))))
                .thenReturn(JsonMapper.builder().build().readTree("{\"name\":\"Dupont\"}"));

        mvc.perform(multipart("/api/visualize/structure").file(file).param("fields", "name", "navs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dupont"));
    }

    @Test
    void classify_returnsType() throws Exception {
        when(visionService.classifyFile(any())).thenReturn(new ZasDocumentType(ZasDocumentType.Type.ID_CARD));

        mvc.perform(multipart("/api/visualize/classify").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ID_CARD"));
    }

    @Test
    void translate_requiresLanguage() throws Exception {
        when(visionService.translateFile(any(), eq("de"))).thenReturn(List.of(new TextTranslation("Hallo", "fr", "de")));

        mvc.perform(multipart("/api/visualize/translate").file(file).param("language", "de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].translatedText").value("Hallo"));
        mvc.perform(multipart("/api/visualize/translate").file(file)).andExpect(status().isBadRequest());
    }

    @Test
    void sumex_returnsExtractedInvoice() throws Exception {
        when(visionService.extractSumexInvoiceFromFile(any())).thenReturn(null);

        mvc.perform(multipart("/api/visualize/sumex").file(file)).andExpect(status().isOk());
    }
}
