package zas.admin.zec.backend.actions.authorize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.config.ExceptionsHandler;
import zas.admin.zec.backend.config.security.ZasUser;
import zas.admin.zec.backend.tools.SecurityLogging;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private static final String GUEST = "00000000-0000-0000-0000-000000000000";

    private final UserService userService = mock(UserService.class);
    private final SecurityLogging securityLogging = mock(SecurityLogging.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserController(userService, securityLogging))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void authenticated_eportalGuest_getsJohnDoeProfileWithoutLookup() throws Exception {
        mvc.perform(get("/api/users/authenticated").principal(new TestingAuthenticationToken(GUEST, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(GUEST))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.status").value("JOHN_DOE"))
                .andExpect(jsonPath("$.internalUser").value(false));
        verify(userService, never()).getByUsername(anyString());
    }

    @Test
    void authenticated_registeredZasUser_returnsStoredProfile() throws Exception {
        when(userService.getByUsername("jdoe")).thenReturn(
                new User("jdoe", "Jane", "Doe", UserStatus.ACTIVE, List.of(Role.USER, Role.ADMIN), List.of("ZAS"), true));

        mvc.perform(get("/api/users/authenticated").principal(zasUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.roles[1]").value("ADMIN"))
                .andExpect(jsonPath("$.internalUser").value(true));
    }

    @Test
    void authenticated_zasUserWithoutAccount_isGuestWithTokenNames() throws Exception {
        when(userService.getByUsername("jdoe")).thenThrow(new IllegalArgumentException("no account"));

        mvc.perform(get("/api/users/authenticated").principal(zasUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GUEST"))
                .andExpect(jsonPath("$.firstName").value("Jeanne"))
                .andExpect(jsonPath("$.lastName").value("Dupont"))
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void authenticated_externalUserWithoutAccount_isGuestWithoutNames() throws Exception {
        when(userService.getByUsername("ext")).thenThrow(new IllegalArgumentException("no account"));

        mvc.perform(get("/api/users/authenticated").principal(new TestingAuthenticationToken("ext", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ext"))
                .andExpect(jsonPath("$.status").value("GUEST"))
                .andExpect(jsonPath("$.firstName").doesNotExist());
    }

    @Test
    void register_returns201WithUserId() throws Exception {
        when(userService.register(eq("jdoe"), any())).thenReturn("uuid-1");

        mvc.perform(post("/api/users").principal(new TestingAuthenticationToken("jdoe", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"organizations\":[\"ZAS\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("uuid-1"));
        verify(userService).register("jdoe", new UserRegistration("Jane", "Doe", List.of("ZAS")));
        verify(securityLogging).logPrivilegeModification("add", "jdoe");
    }

    @Test
    void register_existingUser_returns409() throws Exception {
        when(userService.register(eq("jdoe"), any())).thenThrow(new IllegalArgumentException("exists"));

        mvc.perform(post("/api/users").principal(new TestingAuthenticationToken("jdoe", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Jane\"}"))
                .andExpect(status().isConflict());
        verify(securityLogging, never()).logPrivilegeModification(anyString(), anyString());
    }

    @Test
    void getAllUsers_appliesPagingAndSortParameters() throws Exception {
        when(userService.getAllUsers(any())).thenAnswer(i -> new PageImpl<>(List.of(
                new UserProfile("jdoe", "Jane", "Doe", UserStatus.ACTIVE, List.of("USER"), false)), i.getArgument(0), 11));

        mvc.perform(get("/api/users").param("page", "2").param("pageSize", "5")
                        .param("sortField", "lastName").param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("jdoe"))
                .andExpect(jsonPath("$.totalElements").value(11));

        var captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(userService).getAllUsers(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "lastName"));
    }

    @Test
    void getAllUsers_defaultsSortByStatusDescEvenForEmptySortField() throws Exception {
        when(userService.getAllUsers(any())).thenAnswer(i -> new PageImpl<>(List.of(), i.getArgument(0), 0));

        mvc.perform(get("/api/users")).andExpect(status().isOk());
        mvc.perform(get("/api/users").param("sortField", "")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(userService, org.mockito.Mockito.times(2)).getAllUsers(captor.capture());
        assertThat(captor.getAllValues().get(0).getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "status"));
        assertThat(captor.getAllValues().get(0).getPageSize()).isEqualTo(10);
        // Spring applique defaultValue aux paramètres vides : la branche Sort.unsorted() du controller est inatteignable.
        assertThat(captor.getAllValues().get(1).getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "status"));
    }

    @Test
    void stateTransitions_delegateToServiceAndLogPrivilegeChanges() throws Exception {
        mvc.perform(put("/api/users/jdoe/validate")).andExpect(status().isOk());
        mvc.perform(put("/api/users/jdoe/reactivate")).andExpect(status().isOk());
        mvc.perform(put("/api/users/jdoe/deactivate")).andExpect(status().isOk());
        mvc.perform(put("/api/users/jdoe/internalize")).andExpect(status().isOk());
        mvc.perform(put("/api/users/jdoe/externalize")).andExpect(status().isOk());

        verify(userService).validate("jdoe");
        verify(userService).reactivate("jdoe");
        verify(userService).deactivate("jdoe");
        verify(userService).internalize("jdoe");
        verify(userService).externalize("jdoe");
        verify(securityLogging, org.mockito.Mockito.times(3)).logPrivilegeModification("modify", "jdoe");
    }

    @Test
    void updateRoles_passesRoles() throws Exception {
        mvc.perform(put("/api/users/jdoe/roles").contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk());

        verify(userService).updateRoles("jdoe", List.of("ADMIN"));
    }

    @Test
    void updateRoles_emptyList_returns400() throws Exception {
        mvc.perform(put("/api/users/jdoe/roles").contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.MESSAGE").value("La liste des rôles ne peut pas être vide"));
        verify(userService, never()).updateRoles(anyString(), any());
    }

    private static ZasUser zasUser() {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "jdoe").build();
        return new ZasUser(jwt, List.of(), "jdoe", "Jeanne", "Dupont", "JDU");
    }
}
