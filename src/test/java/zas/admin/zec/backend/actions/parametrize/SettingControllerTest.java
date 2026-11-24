package zas.admin.zec.backend.actions.parametrize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingControllerTest {

    private final SettingService service = mock(SettingService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SettingController(service)).build();
    }

    @Test
    void getSettings_convertsType() throws Exception {
        when(service.getSettings(SettingType.LLM_MODEL)).thenReturn(List.of("gpt", "mistral"));

        mvc.perform(get("/api/settings").param("type", "LLM_MODEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1]").value("mistral"));
    }

    @Test
    void getSettings_unknownType_returns400() throws Exception {
        mvc.perform(get("/api/settings").param("type", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void getTags_splitsCommaSeparatedSources() throws Exception {
        when(service.getTags(List.of("rh", "avs"))).thenReturn(List.of("vacances"));

        mvc.perform(get("/api/settings/tags").param("sources", "rh,avs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("vacances"));
        verify(service).getTags(List.of("rh", "avs"));
    }
}
