package zas.admin.zec.backend.config.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ApiKeyAuthenticationConverter implements AuthenticationConverter {

    // Attend: X-API-Key: ak_<keyId>.<secret>
    // ou Authorization: ApiKey ak_<keyId>.<secret>
    @Override
    public ApiKeyAuthenticationToken convert(HttpServletRequest request) {
        String token = extractFromHeaders(request).orElse(null);
        if (token == null) return null;

        String[] parts = token.trim().split("\\.", 2);
        if (parts.length != 2) return null;

        String keyId = parts[0];
        String secret = parts[1];
        if (keyId.isBlank() || secret.isBlank()) return null;

        // Retire éventuel préfixe "ak_"
        if (keyId.startsWith("ak_")) keyId = keyId.substring(3);

        return new ApiKeyAuthenticationToken(keyId, secret);
    }

    private Optional<String> extractFromHeaders(HttpServletRequest request) {
        String xApiKey = request.getHeader("X-API-Key");
        if (xApiKey != null && !xApiKey.isBlank()) {
            return Optional.of(xApiKey);
        }

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "ApiKey ", 0, 7)) {
            return Optional.of(auth.substring(7).trim());
        }

        return Optional.empty();
    }
}
