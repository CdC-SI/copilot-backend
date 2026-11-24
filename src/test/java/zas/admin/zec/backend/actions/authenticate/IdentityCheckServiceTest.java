package zas.admin.zec.backend.actions.authenticate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import zas.admin.zec.backend.config.properties.IdentityCheckProperties;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    @Test
    @DisplayName("startIdentityCheck posts the request to the VIPS transactions API")
    void startIdentityCheck_postsTransaction() {
        var builder = RestClient.builder().baseUrl("https://vips.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new IdentityCheckService(builder.build(), identityCheckProperties);
        var transactionId = UUID.randomUUID();
        server.expect(requestTo("https://vips.test/api/v2/transactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.identificationMethod").value("auto_id"))
                .andExpect(jsonPath("$.userdata.firstname").value("Jeanne"))
                .andRespond(withSuccess("{\"vipsTransactionId\":\"" + transactionId + "\",\"endpoint\":\"https://vips/go\"}",
                        MediaType.APPLICATION_JSON));

        var person = new IdentityPersonData("jdoe", "Jeanne", "Dupont", "fr", null, null, null, null, null,
                null, null, null, null, null, null, null);
        var response = service.startIdentityCheck(new IdentityCheckRequest(person, "auto_id", "http://cb", true, false));

        assertEquals(transactionId, response.vipsTransactionId());
        assertEquals("https://vips/go", response.endpoint());
        server.verify();
    }

    @Test
    @DisplayName("getIdentityCheckStatus reads the transaction state; HTTP errors are propagated")
    void getIdentityCheckStatus_readsTransaction() {
        var builder = RestClient.builder().baseUrl("https://vips.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new IdentityCheckService(builder.build(), identityCheckProperties);
        var ok = UUID.randomUUID();
        var ko = UUID.randomUUID();
        server.expect(requestTo("https://vips.test/api/v2/transactions/" + ok))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"" + ok + "\",\"state\":\"SUCCESS\",\"identificationMethod\":\"auto_id\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://vips.test/api/v2/transactions/" + ko))
                .andRespond(withServerError());

        var status = service.getIdentityCheckStatus(ok);

        assertEquals("SUCCESS", status.state());
        assertEquals(ok, status.id());
        assertThrows(HttpServerErrorException.class, () -> service.getIdentityCheckStatus(ko));
        server.verify();
    }
}


