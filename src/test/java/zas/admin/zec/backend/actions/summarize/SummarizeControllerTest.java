package zas.admin.zec.backend.actions.summarize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.core.JacksonException;
import zas.admin.zec.backend.actions.summarize.jms.GaimeJmsService;
import zas.admin.zec.backend.config.ExceptionsHandler;
import zas.admin.zec.backend.config.security.ZasUser;
import zas.admin.zec.backend.tools.SecurityLogging;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SummarizeControllerTest {

    private final SummarizeService service = mock(SummarizeService.class);
    private final GaimeJmsService gaime = mock(GaimeJmsService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SummarizeController(service, gaime, mock(SecurityLogging.class)))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void createSummary_newTask_returns201() throws Exception {
        when(service.startSummarization("756.1234.5678.97"))
                .thenReturn(new SummaryTaskCreatedResponse(1L, "756.1234.5678.97", SummaryTaskStatus.EN_COURS, "lancée"));

        mvc.perform(post("/api/summaries/756.1234.5678.97"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EN_COURS"));
    }

    @Test
    void createSummary_alreadyCompleted_returns200() throws Exception {
        when(service.startSummarization("756"))
                .thenReturn(new SummaryTaskCreatedResponse(1L, "756", SummaryTaskStatus.TERMINEE, "existante"));

        mvc.perform(post("/api/summaries/756")).andExpect(status().isOk());
    }

    @Test
    void getAllTasks_returnsList() throws Exception {
        when(service.getAllTasks()).thenReturn(List.of(
                new SummaryTaskResponse(1L, "756", SummaryTaskStatus.ERREUR, LocalDateTime.now(), LocalDateTime.now())));

        mvc.perform(get("/api/summaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ERREUR"));
    }

    @Test
    void getSummaryDetail_returnsMarkdown() throws Exception {
        when(service.getSummaryDetail(1L)).thenReturn(new SummaryDetailResponse(1L, "756", "# Synthèse", LocalDateTime.now()));

        mvc.perform(get("/api/summaries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryMarkdown").value("# Synthèse"));
    }

    @Test
    void getSummaryDetail_unknown_returns404() throws Exception {
        when(service.getSummaryDetail(9L)).thenThrow(new TaskNotFoundException("Tâche 9 introuvable"));

        mvc.perform(get("/api/summaries/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Tâche introuvable"))
                .andExpect(jsonPath("$.message").value("Tâche 9 introuvable"));
    }

    @Test
    void getSummaryDetail_notCompleted_returns409() throws Exception {
        when(service.getSummaryDetail(2L)).thenThrow(new TaskNotCompletedException("en cours"));

        mvc.perform(get("/api/summaries/2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Tâche non terminée"));
    }

    @Test
    void restartSummary_returns200() throws Exception {
        when(service.restartSummarization(1L)).thenReturn(new SummaryTaskCreatedResponse(1L, "756", SummaryTaskStatus.EN_COURS, "relancée"));

        mvc.perform(put("/api/summaries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("relancée"));
    }

    @Test
    void openReferences_nonZasUser_returns401WithoutLookup() throws Exception {
        mvc.perform(post("/api/summaries/1/open-references").principal(new TestingAuthenticationToken("ext", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Utilisateur non authentifié"));
        verifyNoInteractions(service, gaime);
    }

    @Test
    void openReferences_noReferences_returns204() throws Exception {
        when(service.getTaskReferences(1L)).thenReturn(List.of());

        mvc.perform(post("/api/summaries/1/open-references").principal(zasUser()))
                .andExpect(status().isNoContent());
        verifyNoInteractions(gaime);
    }

    @Test
    void openReferences_sendsJmsMessageWithUserVisa() throws Exception {
        when(service.getTaskReferences(1L)).thenReturn(List.of("tok1", "tok2"));

        mvc.perform(post("/api/summaries/1/open-references").principal(zasUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referencesCount").value(2))
                .andExpect(jsonPath("$.taskId").value(1));
        verify(gaime).sendOpenDocumentsMessage("JDU", List.of("tok1", "tok2"));
    }

    @Test
    void openReferences_serializationFailure_returns500() throws Exception {
        when(service.getTaskReferences(1L)).thenReturn(List.of("tok1"));
        var failure = mock(JacksonException.class);
        when(failure.getMessage()).thenReturn("sérialisation impossible");
        doThrow(failure).when(gaime).sendOpenDocumentsMessage(any(), any());

        mvc.perform(post("/api/summaries/1/open-references").principal(zasUser()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("sérialisation impossible"));
    }

    @Test
    void openReferences_taskNotCompleted_returns409() throws Exception {
        when(service.getTaskReferences(1L)).thenThrow(new TaskNotCompletedException("en cours"));

        mvc.perform(post("/api/summaries/1/open-references").principal(zasUser()))
                .andExpect(status().isConflict());
    }

    private static ZasUser zasUser() {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "jdoe").build();
        return new ZasUser(jwt, List.of(), "jdoe", "Jeanne", "Dupont", "JDU");
    }
}
