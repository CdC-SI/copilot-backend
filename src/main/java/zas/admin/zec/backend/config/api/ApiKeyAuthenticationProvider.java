package zas.admin.zec.backend.config.api;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.entity.ApiClientEntity;
import zas.admin.zec.backend.persistence.entity.ApiClientKeyEntity;
import zas.admin.zec.backend.persistence.repository.ApiClientKeyRepository;

import java.time.Instant;

@Component
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiClientKeyRepository keyRepository;
    private final Argon2PasswordEncoder encoder;

    public ApiKeyAuthenticationProvider(ApiClientKeyRepository keyRepository, Argon2PasswordEncoder encoder) {
        this.keyRepository = keyRepository;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        ApiKeyAuthenticationToken unauth = (ApiKeyAuthenticationToken) authentication;

        String keyId = unauth.keyId();
        String rawSecret = unauth.rawSecret();

        ApiClientKeyEntity key = keyRepository
                .findActiveByKeyId(keyId)
                .orElseThrow(() -> new RuntimeException("API key not found"));

        if (key.isRevoked() || (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now()))) {
            throw new RuntimeException("API key is revoked or expired");
        }

        if (!encoder.matches(rawSecret, key.getSecretHash())) {
            throw new RuntimeException("Invalid API key");
        }

        key.touchLastUsed();
        keyRepository.save(key);

        ApiClientEntity client = key.getClient();
        var authorities = client.rolesAsAuthorities();
        var principal = new ApiClientPrincipal(client.getId(), client.getName(), client.getReference());

        return new ApiKeyAuthenticationToken(principal, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
