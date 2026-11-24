package zas.admin.zec.backend.actions.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.config.ExceptionsHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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

class WorkspaceControllerTest {

    private final WorkspaceService service = mock(WorkspaceService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(service))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void getAll_returnsWorkspaces() throws Exception {
        when(service.getAll()).thenReturn(List.of(dto("AVS")));

        mvc.perform(get("/api/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("AVS"))
                .andExpect(jsonPath("$[0].sources[0]").value("s1"));
    }

    @Test
    void getByName_unknown_returns404WithMessage() throws Exception {
        when(service.getByName("X")).thenThrow(new WorkspaceNotFoundException("Workspace introuvable : X"));

        mvc.perform(get("/api/workspaces/X"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Workspace introuvable"))
                .andExpect(jsonPath("$.message").value("Workspace introuvable : X"));
    }

    @Test
    void create_returns201() throws Exception {
        when(service.create(any())).thenReturn(dto("AVS"));

        mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"AVS\",\"sources\":[\"s1\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("AVS"));
        verify(service).create(new CreateWorkspaceRequest("AVS", null, null, List.of("s1")));
    }

    @Test
    void create_blankName_isRejectedBeforeTheService() throws Exception {
        mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.MESSAGE").value("Le nom du workspace est obligatoire"));
        verify(service, never()).create(any());
    }

    @Test
    void create_existing_returns409() throws Exception {
        when(service.create(any())).thenThrow(new WorkspaceAlreadyExistsException("existe"));

        mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"AVS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Workspace déjà existant"));
    }

    @Test
    void update_unknownSource_returns400() throws Exception {
        when(service.update(any(), any())).thenThrow(new UnknownSourceException("Source inconnue : ghost"));

        mvc.perform(put("/api/workspaces/AVS").contentType(MediaType.APPLICATION_JSON).content("{\"sources\":[\"ghost\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Source inconnue"))
                .andExpect(jsonPath("$.message").value("Source inconnue : ghost"));
    }

    @Test
    void update_returnsUpdatedWorkspace() throws Exception {
        when(service.update(any(), any())).thenReturn(dto("AVS"));

        mvc.perform(put("/api/workspaces/AVS").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("desc"));
        verify(service).update("AVS", new UpdateWorkspaceRequest("d", null, null));
    }

    @Test
    void delete_returns200() throws Exception {
        mvc.perform(delete("/api/workspaces/AVS")).andExpect(status().isOk());

        verify(service).delete("AVS");
    }

    private static WorkspaceDto dto(String name) {
        return new WorkspaceDto(1L, name, "desc", List.of(), List.of("s1"), LocalDateTime.now(), LocalDateTime.now());
    }
}
