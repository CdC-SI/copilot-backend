package zas.admin.zec.backend.actions.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.tools.api.ApiKeyService;
import zas.admin.zec.backend.tools.api.GeneratedKey;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicKeyControllerTest {

    @Test
    void createApiKey_returnsSecretOnceWithUsageInstructions() throws Exception {
        var service = mock(ApiKeyService.class);
        var clientId = UUID.randomUUID();
        var expiresAt = LocalDate.of(2030, 6, 30).atStartOfDay(ZoneId.systemDefault()).toInstant();
        when(service.createKey(clientId, "ci", expiresAt)).thenReturn(new GeneratedKey("k123", "s3cr3t", "ci", expiresAt));
        var mvc = MockMvcBuilders.standaloneSetup(new PublicKeyController(service)).build();

        mvc.perform(post("/api/api-keys").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"label\":\"ci\",\"expiresAt\":\"2030-06-30\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("keyId : k123"),
                        containsString("secret : s3cr3t"),
                        containsString("expire le : " + expiresAt),
                        containsString("X-API-Key: ak_<keyId>.<secret>"))));
    }
}
