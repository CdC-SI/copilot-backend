package zas.admin.zec.backend.tools.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import zas.admin.zec.backend.persistence.entity.ApiClientEntity;
import zas.admin.zec.backend.persistence.entity.ApiClientKeyEntity;
import zas.admin.zec.backend.persistence.repository.ApiClientKeyRepository;
import zas.admin.zec.backend.persistence.repository.ApiClientRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private Argon2PasswordEncoder encoder;
    @Mock
    private ApiClientRepository clientRepository;
    @Mock
    private ApiClientKeyRepository keyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @Test
    @DisplayName("createKey saves key entity when client exists")
    void createKey_savesKey_whenClientExists() {
        UUID clientId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        ApiClientEntity client = new ApiClientEntity();

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(encoder.encode(anyString())).thenReturn("hashed-secret");

        GeneratedKey result = apiKeyService.createKey(clientId, "test-label", expiresAt);

        assertNotNull(result.keyId());
        assertNotNull(result.secret());
        assertEquals("test-label", result.label());
        assertEquals(expiresAt, result.expiresAt());

        ArgumentCaptor<ApiClientKeyEntity> captor = ArgumentCaptor.forClass(ApiClientKeyEntity.class);
        verify(keyRepository).save(captor.capture());
        assertEquals(client, captor.getValue().getClient());
        assertFalse(captor.getValue().isRevoked());
    }

    @Test
    @DisplayName("createKey throws when client not found")
    void createKey_throws_whenClientNotFound() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> apiKeyService.createKey(clientId, "label", Instant.now()));
    }
}

