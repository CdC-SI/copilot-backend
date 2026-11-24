package zas.admin.zec.backend.actions.source;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.config.ExceptionsHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SourceControllerTest {

    private final SourceService service = mock(SourceService.class);
    private final TestingAuthenticationToken principal = new TestingAuthenticationToken("jdoe", null);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SourceController(service))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void getAll_returnsSources() throws Exception {
        when(service.getAll()).thenReturn(List.of(dto("rh", List.of())));

        mvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("rh"));
    }

    @Test
    void getByName_passesTheAuthenticatedUserForContentFiltering() throws Exception {
        when(service.getByName("rh", "jdoe")).thenReturn(dto("rh", List.of(new SourceContentDto("Règlement", "https://r"))));

        mvc.perform(get("/api/sources/rh").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contents[0].title").value("Règlement"));
    }

    @Test
    void getByName_unknown_returns404() throws Exception {
        when(service.getByName("x", "jdoe")).thenThrow(new SourceNotFoundException("Source introuvable : x"));

        mvc.perform(get("/api/sources/x").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Source introuvable"));
    }

    @Test
    void create_returns201() throws Exception {
        when(service.create(any())).thenReturn(dto("rh", List.of()));

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"rh\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("rh"));
    }

    @Test
    void create_tooLongName_isRejected() throws Exception {
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.MESSAGE").value("Le nom de la source ne peut pas dépasser 255 caractères"));
        verify(service, never()).create(any());
    }

    @Test
    void create_existing_returns409() throws Exception {
        when(service.create(any())).thenThrow(new SourceAlreadyExistsException("existe"));

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"rh\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Source déjà existante"));
    }

    @Test
    void update_passesTheAuthenticatedUser() throws Exception {
        when(service.update(eq("rh"), any(), eq("jdoe"))).thenReturn(dto("rh", List.of()));

        mvc.perform(put("/api/sources/rh").principal(principal).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"d\",\"hypotheticalQuestions\":[\"Q\"]}"))
                .andExpect(status().isOk());
        verify(service).update("rh", new UpdateSourceRequest("d", List.of("Q")), "jdoe");
    }

    @Test
    void delete_returns200() throws Exception {
        mvc.perform(delete("/api/sources/rh")).andExpect(status().isOk());

        verify(service).delete("rh");
    }

    private static SourceDto dto(String name, List<SourceContentDto> contents) {
        return new SourceDto(1L, name, "desc", List.of(), contents, LocalDateTime.now(), LocalDateTime.now());
    }
}
