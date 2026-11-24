package zas.admin.zec.backend.actions.authenticate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.actions.authorize.User;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.authorize.UserStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdentityCheckControllerTest {

    private final IdentityCheckService service = mock(IdentityCheckService.class);
    private final UserService userService = mock(UserService.class);
    private final TestingAuthenticationToken principal = new TestingAuthenticationToken("jdoe", null);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new IdentityCheckController(service, userService)).build();
    }

    @Test
    void start_buildsPersonDataFromAccountAndBodyAndStoresTransaction() throws Exception {
        var transactionId = UUID.randomUUID();
        when(userService.getByUsername("jdoe"))
                .thenReturn(new User("jdoe", "Jeanne", "Dupont", UserStatus.ACTIVE, List.of(), List.of(), false));
        when(service.createRequestForUser(any())).thenReturn(null);
        when(service.startIdentityCheck(any())).thenReturn(new IdentityCheckResponse(transactionId, "https://vips/start"));

        mvc.perform(post("/api/identity-check/start").principal(principal).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"1980-01-01\",\"city\":\"Genève\",\"idType\":\"PASSPORT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value("https://vips/start"));

        var captor = ArgumentCaptor.forClass(IdentityPersonData.class);
        verify(service).createRequestForUser(captor.capture());
        assertThat(captor.getValue().userAnonymousId()).isEqualTo("jdoe");
        assertThat(captor.getValue().firstname()).isEqualTo("Jeanne");
        assertThat(captor.getValue().correspondingLang()).isEqualTo("fr");
        assertThat(captor.getValue().city()).isEqualTo("Genève");
        assertThat(captor.getValue().idType()).isEqualTo("PASSPORT");
        assertThat(captor.getValue().email()).isNull();
        verify(service).saveUserIdentityCheck("jdoe", transactionId);
    }

    @Test
    void status_withoutTransaction_returnsNotStarted() throws Exception {
        when(service.getUserIdentityCheck("jdoe")).thenReturn(null);
        when(service.getIdentityCheckStatus(null)).thenReturn(null);

        mvc.perform(get("/api/identity-check/status").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_STARTED"));
    }

    @Test
    void status_returnsRemoteStatus() throws Exception {
        var transactionId = UUID.randomUUID();
        when(service.getUserIdentityCheck("jdoe")).thenReturn(transactionId);
        when(service.getIdentityCheckStatus(transactionId))
                .thenReturn(new IdentityCheckStatus(transactionId, "SUCCESS", "VIDEO", "2024-01-01T10:00:00", null));

        mvc.perform(get("/api/identity-check/status").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCESS"))
                .andExpect(jsonPath("$.identificationMethod").value("VIDEO"));
    }
}
