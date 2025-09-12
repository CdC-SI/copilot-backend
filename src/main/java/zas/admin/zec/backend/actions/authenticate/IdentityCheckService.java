package zas.admin.zec.backend.actions.authenticate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import zas.admin.zec.backend.actions.authorize.User;
import zas.admin.zec.backend.config.properties.IdentityCheckProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Service
public class IdentityCheckService {

    private final RestClient restClient;
    private final IdentityCheckProperties identityCheckProperties;
    private final Map<String, UUID> usersIdentityCheck = new HashMap<>();

    public IdentityCheckService(@Qualifier("identityCheckRestClient") RestClient webClient, IdentityCheckProperties identityCheckProperties) {
        this.restClient = webClient;
        this.identityCheckProperties = identityCheckProperties;
    }

    public IdentityCheckResponse startIdentityCheck(IdentityCheckRequest request) {
        return restClient.post()
                .uri("/api/v2/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(IdentityCheckResponse.class)
                .getBody();
    }

    public IdentityCheckStatus getIdentityCheckStatus(UUID identyCheckId) {
        if (identyCheckId == null) {
            return null;
        }
        return restClient.get()
                .uri("/api/v2/transactions/" + identyCheckId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(IdentityCheckStatus.class)
                .getBody();
    }

    public void saveUserIdentityCheck(String username, UUID identyCheckId) {
        usersIdentityCheck.put(username, identyCheckId);
    }

    public UUID getUserIdentityCheck(String username) {
        return usersIdentityCheck.get(username);
    }

    public IdentityCheckRequest createRequestForUser(IdentityPersonData identityPersonData) {

        return new IdentityCheckRequest(
                identityPersonData,
                "auto_id",
                identityCheckProperties.callBackUrl(),
                true,
                false);
    }
}
