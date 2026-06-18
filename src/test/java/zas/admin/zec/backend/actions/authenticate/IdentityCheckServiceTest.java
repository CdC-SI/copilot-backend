package zas.admin.zec.backend.actions.authenticate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import zas.admin.zec.backend.config.properties.IdentityCheckProperties;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityCheckServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private IdentityCheckProperties identityCheckProperties;

    @InjectMocks
    private IdentityCheckService identityCheckService;

    @Test
    @DisplayName("saveUserIdentityCheck and getUserIdentityCheck work correctly")
    void saveAndGetUserIdentityCheck() {
        UUID checkId = UUID.randomUUID();
        identityCheckService.saveUserIdentityCheck("user1", checkId);

        assertEquals(checkId, identityCheckService.getUserIdentityCheck("user1"));
    }

    @Test
    @DisplayName("getUserIdentityCheck returns null for unknown user")
    void getUserIdentityCheck_returnsNull_forUnknownUser() {
        assertNull(identityCheckService.getUserIdentityCheck("unknown"));
    }

    @Test
    @DisplayName("getIdentityCheckStatus returns null when id is null")
    void getIdentityCheckStatus_returnsNull_whenIdIsNull() {
        assertNull(identityCheckService.getIdentityCheckStatus(null));
    }

    @Test
    @DisplayName("createRequestForUser builds correct request")
    void createRequestForUser_buildsCorrectRequest() {
        when(identityCheckProperties.callBackUrl()).thenReturn("http://callback");

        IdentityPersonData personData = mock(IdentityPersonData.class);
        IdentityCheckRequest result = identityCheckService.createRequestForUser(personData);

        assertEquals(personData, result.userdata());
        assertEquals("auto_id", result.identificationMethod());
        assertEquals("http://callback", result.redirectUrl());
        assertTrue(result.gtcAccepted());
        assertFalse(result.background());
    }
}


