package zas.admin.zec.backend.actions.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zas.admin.zec.backend.config.RequireAdmin;
import zas.admin.zec.backend.tools.api.ApiKeyService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/api-keys")
public class PublicKeyController {

    private final ApiKeyService apiKeyService;

    public PublicKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    public record ApiKeyRequest(UUID clientId, String label, LocalDate expiresAt) {}

    @RequireAdmin
    @PostMapping
    public String createApiKey(@RequestBody ApiKeyRequest request) {
        var key = apiKeyService.createKey(
                request.clientId,
                request.label,
                request.expiresAt.atStartOfDay(ZoneId.systemDefault()).toInstant());

        return """
                Cette clé API a été créée avec succès. Veuillez la copier et la conserver en lieu sûr, car elle ne sera plus affichée.
                keyId : %s
                secret : %s
                expire le : %s
                Utilisation :
                    curl -H 'X-API-Key: ak_<keyId>.<secret>' ...
                 ou curl -H 'Authorization: ApiKey ak_<keyId>.<secret>' ...
                """.formatted(key.keyId(), key.secret(), key.expiresAt());
    }
}
