package zas.admin.zec.backend.actions.sourcerequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.config.ExceptionsHandler;
import zas.admin.zec.backend.config.security.ZasUser;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SourceRequestControllerTest {

    private final SourceRequestService service = mock(SourceRequestService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SourceRequestController(service))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void createRequest_usesTheVisaOfTheUserAsRequester() throws Exception {
        when(service.createRequest(any(), any())).thenReturn(dto(1L, RequestStatus.WAITING));

        mvc.perform(post("/api/source-requests").principal(zasUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"rh\",\"description\":\"Règlement RH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"));
        verify(service).createRequest(new CreateSourceRequest("rh", "Règlement RH"), "JDU");
    }

    @Test
    void createRequest_missingDescription_returns400() throws Exception {
        mvc.perform(post("/api/source-requests").principal(zasUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"rh\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.MESSAGE").value("La description est obligatoire"));
        verify(service, never()).createRequest(any(), any());
    }

    @Test
    void getMyRequests_passesVisaAndLimit() throws Exception {
        when(service.getUserRequests("JDU", 3)).thenReturn(List.of(dto(1L, RequestStatus.WAITING)));

        mvc.perform(get("/api/source-requests/my").param("limit", "3").principal(zasUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAllRequests_withoutLimit() throws Exception {
        when(service.getAllRequests(null)).thenReturn(List.of(dto(1L, RequestStatus.WAITING), dto(2L, RequestStatus.INTEGRATED)));

        mvc.perform(get("/api/source-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getRequestsByStatus_convertsPathVariable() throws Exception {
        when(service.getRequestsByStatus(RequestStatus.PROCESSING)).thenReturn(List.of(dto(1L, RequestStatus.PROCESSING)));

        mvc.perform(get("/api/source-requests/by-status/PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PROCESSING"));
    }

    @Test
    void getById_ofAnotherUser_returns404() throws Exception {
        when(service.getById(5L, "JDU")).thenThrow(new SourceRequestNotFoundException("Source request not found or access denied: 5"));

        mvc.perform(get("/api/source-requests/5").principal(zasUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Demande introuvable"));
    }

    @Test
    void getById_ownRequest_returns200() throws Exception {
        when(service.getById(1L, "JDU")).thenReturn(dto(1L, RequestStatus.WAITING));

        mvc.perform(get("/api/source-requests/1").principal(zasUser())).andExpect(status().isOk());
    }

    @Test
    void deleteById_restrictsToVisa() throws Exception {
        mvc.perform(delete("/api/source-requests/1").principal(zasUser())).andExpect(status().isOk());

        verify(service).deleteById(1L, "JDU");
    }

    @Test
    void getByIdAdmin_returnsRequest() throws Exception {
        when(service.getByIdAdmin(1L)).thenReturn(dto(1L, RequestStatus.WAITING));

        mvc.perform(get("/api/source-requests/admin/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requesterUsername").value("JDU"));
    }

    @Test
    void updateStatus_invalidTransition_returns400() throws Exception {
        when(service.updateStatus(1L, RequestStatus.WAITING))
                .thenThrow(new InvalidStatusTransitionException("Invalid status transition from INTEGRATED to WAITING"));

        mvc.perform(patch("/api/source-requests/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WAITING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Transition de statut invalide"));
    }

    @Test
    void updateStatus_missingStatus_returns400WithoutCallingService() throws Exception {
        mvc.perform(patch("/api/source-requests/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.MESSAGE").value("Le statut est obligatoire"));
        verify(service, never()).updateStatus(anyLong(), any());
    }

    @Test
    void updateStatus_valid_returnsUpdatedRequest() throws Exception {
        when(service.updateStatus(1L, RequestStatus.INTEGRATED)).thenReturn(dto(1L, RequestStatus.INTEGRATED));

        mvc.perform(patch("/api/source-requests/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTEGRATED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTEGRATED"));
    }

    private static SourceRequestDto dto(Long id, RequestStatus status) {
        return new SourceRequestDto(id, "rh", "desc", "JDU", status, LocalDateTime.now(), LocalDateTime.now());
    }

    private static ZasUser zasUser() {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "jdoe").build();
        return new ZasUser(jwt, List.of(), "jdoe", "Jeanne", "Dupont", "JDU");
    }
}
