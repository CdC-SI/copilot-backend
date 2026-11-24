package zas.admin.zec.backend.actions.analyze;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.actions.analyze.model.FeedbackReportConfig;
import zas.admin.zec.backend.config.ExceptionsHandler;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;
import zas.admin.zec.backend.persistence.entity.FeedbackStatus;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackKPIControllerTest {

    private final FeedbackQueryService queryService = mock(FeedbackQueryService.class);
    private final FeedbackReportConfigService configService = mock(FeedbackReportConfigService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FeedbackKPIController(queryService, configService))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void stats_passesRange() throws Exception {
        when(queryService.stats("7d")).thenReturn(new FeedbackDTO.Stats(4, 3, 1, 0.75, List.of(), List.of(), List.of(), List.of()));

        mvc.perform(get("/api/feedback/stats").param("range", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positiveRate").value(0.75));
    }

    @Test
    void messages_convertsFilters() throws Exception {
        when(queryService.listMessages("30d", true, FeedbackStatus.NEW, FeedbackCategory.WRONG_ANSWER)).thenReturn(List.of(message()));

        mvc.perform(get("/api/feedback/messages").param("range", "30d").param("includeDetails", "true")
                        .param("status", "NEW").param("category", "WRONG_ANSWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageUuid").value("m1"));
    }

    @Test
    void messages_defaultsWithoutFilters() throws Exception {
        mvc.perform(get("/api/feedback/messages")).andExpect(status().isOk());

        verify(queryService).listMessages(null, false, null, null);
    }

    @Test
    void sources_convertsFilters() throws Exception {
        mvc.perform(get("/api/feedback/sources").param("status", "TREATED")).andExpect(status().isOk());

        verify(queryService).listSources(null, FeedbackStatus.TREATED, null);
    }

    @Test
    void updateStatuses_delegateToService() throws Exception {
        when(queryService.updateMessageFeedbackStatus(1L, FeedbackStatus.OBSOLETE)).thenReturn(message());

        mvc.perform(patch("/api/feedback/messages/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OBSOLETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        mvc.perform(patch("/api/feedback/sources/2/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"TREATED\"}"))
                .andExpect(status().isOk());

        verify(queryService).updateSourceFeedbackStatus(2L, FeedbackStatus.TREATED);
    }

    @Test
    void reportConfig_getAndValidUpdate() throws Exception {
        var config = new FeedbackReportConfig(true, "0 0 8 * * MON", "Europe/Zurich", List.of("a@zas.ch"), 7);
        when(configService.get()).thenReturn(config);
        when(configService.update(config)).thenReturn(config);

        mvc.perform(get("/api/feedback/report-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients[0]").value("a@zas.ch"));
        mvc.perform(put("/api/feedback/report-config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cronExpression\":\"0 0 8 * * MON\",\"zoneId\":\"Europe/Zurich\",\"recipients\":[\"a@zas.ch\"],\"lookbackDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookbackDays").value(7));
    }

    @Test
    void reportConfig_invalidCron_returns400WithoutUpdate() throws Exception {
        mvc.perform(put("/api/feedback/report-config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cronExpression\":\"tous les lundis\",\"zoneId\":\"Europe/Zurich\",\"recipients\":[\"a@zas.ch\"],\"lookbackDays\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.MESSAGE").value("L'expression cron est invalide"));
        verify(configService, never()).update(any());
    }

    private static FeedbackDTO.MessageFeedback message() {
        return new FeedbackDTO.MessageFeedback(1L, "u1", "c1", "m1", 1, null, Instant.parse("2024-01-01T10:00:00Z"),
                null, null, List.of(), FeedbackStatus.NEW, FeedbackCategory.WRONG_ANSWER);
    }
}
