package zas.admin.zec.backend.tools.api;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.entity.ApiClientKeyEntity;
import zas.admin.zec.backend.persistence.repository.ApiClientKeyRepository;
import zas.admin.zec.backend.persistence.repository.ApiClientRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Argon2PasswordEncoder encoder;
    private final ApiClientRepository clientRepository;
    private final ApiClientKeyRepository keyRepository;

    public ApiKeyService(Argon2PasswordEncoder encoder, ApiClientRepository clientRepository, ApiClientKeyRepository keyRepository) {
        this.encoder = encoder;
        this.clientRepository = clientRepository;
        this.keyRepository = keyRepository;
    }

    public GeneratedKey createKey(UUID clientId, String label, Instant expiresAt) {
        String keyId = generateKeyId();
        String secret = generateSecret();

        clientRepository.findById(clientId).ifPresentOrElse(client -> {
            ApiClientKeyEntity key = new ApiClientKeyEntity();
            key.setClient(client);
            key.setKeyId(keyId);
            key.setLabel(label);
            key.setSecretHash(encoder.encode(secret));
            key.setRevoked(false);
            key.setExpiresAt(expiresAt);
            keyRepository.save(key);
        }, () -> {
            throw new IllegalArgumentException("Client not found");
        });

        return new GeneratedKey(keyId, secret, label, expiresAt);
    }

    private String generateKeyId() {
        byte[] bytes = new byte[8];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
