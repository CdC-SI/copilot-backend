package zas.admin.zec.backend.config.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import zas.admin.zec.backend.persistence.entity.ApiClientEntity;
import zas.admin.zec.backend.persistence.entity.ApiClientKeyEntity;
import zas.admin.zec.backend.persistence.repository.ApiClientKeyRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authentification par clé API de l'API publique : extraction des en-têtes et validation de la clé.
 */
class ApiKeyAuthenticationTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();

    private final ApiKeyAuthenticationConverter converter = new ApiKeyAuthenticationConverter();
    private final ApiClientKeyRepository repository = mock(ApiClientKeyRepository.class);
    private final Argon2PasswordEncoder encoder = mock(Argon2PasswordEncoder.class);
    private final ApiKeyAuthenticationProvider provider = new ApiKeyAuthenticationProvider(repository, encoder);

    @Test
    void converter_readsXApiKeyHeaderAndStripsPrefix() {
        var token = converter.convert(request("X-API-Key", "ak_key1.s3cr3t"));

        assertThat(token.keyId()).isEqualTo("key1");
        assertThat(token.rawSecret()).isEqualTo("s3cr3t");
        assertThat(token.isAuthenticated()).isFalse();
    }

    @Test
    void converter_readsAuthorizationApiKeySchemeCaseInsensitively() {
        var token = converter.convert(request("Authorization", "apikey key2.secret.with.dots"));

        assertThat(token.keyId()).isEqualTo("key2");
        assertThat(token.rawSecret()).isEqualTo("secret.with.dots");
    }

    @Test
    void converter_ignoresMissingOrMalformedKeys() {
        assertThat(converter.convert(new MockHttpServletRequest())).isNull();
        assertThat(converter.convert(request("Authorization", "Bearer abc.def"))).isNull();
        assertThat(converter.convert(request("X-API-Key", "sans-point"))).isNull();
        assertThat(converter.convert(request("X-API-Key", "ak_key. "))).isNull();
        assertThat(converter.convert(request("X-API-Key", " "))).isNull();
    }

    @Test
    void provider_validKey_authenticatesClientWithItsRolesAndTouchesKey() {
        var key = key(false, Instant.now().plusSeconds(3600));
        when(repository.findActiveByKeyId("key1")).thenReturn(Optional.of(key));
        when(encoder.matches("s3cr3t", "hash")).thenReturn(true);

        var auth = provider.authenticate(new ApiKeyAuthenticationToken("key1", "s3cr3t"));

        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getPrincipal()).isEqualTo(new ApiClientPrincipal(CLIENT_ID, "Client", "REF-1"));
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_API");
        assertThat(ReflectionTestUtils.getField(key, "lastUsedAt")).isNotNull();
        verify(repository).save(key);
    }

    @Test
    void provider_keyWithoutExpiration_isAccepted() {
        when(repository.findActiveByKeyId("key1")).thenReturn(Optional.of(key(false, null)));
        when(encoder.matches("s3cr3t", "hash")).thenReturn(true);

        assertThat(provider.authenticate(new ApiKeyAuthenticationToken("key1", "s3cr3t")).isAuthenticated()).isTrue();
    }

    /**
     * Les refus sont des AuthenticationException (BadCredentialsException) : le failure handler de l'AuthenticationFilter
     * de SecurityConfig répond 401 JSON.
     */
    @Test
    void provider_refusals_areBadCredentials() {
        when(repository.findActiveByKeyId("unknown")).thenReturn(Optional.empty());
        when(repository.findActiveByKeyId("revoked")).thenReturn(Optional.of(key(true, null)));
        when(repository.findActiveByKeyId("expired")).thenReturn(Optional.of(key(false, Instant.now().minusSeconds(60))));
        when(repository.findActiveByKeyId("wrong")).thenReturn(Optional.of(key(false, null)));
        when(encoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(new ApiKeyAuthenticationToken("unknown", "x")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("API key not found");
        assertThatThrownBy(() -> provider.authenticate(new ApiKeyAuthenticationToken("revoked", "x")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("API key is revoked or expired");
        assertThatThrownBy(() -> provider.authenticate(new ApiKeyAuthenticationToken("expired", "x")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("API key is revoked or expired");
        assertThatThrownBy(() -> provider.authenticate(new ApiKeyAuthenticationToken("wrong", "bad")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid API key");
        verify(repository, never()).save(any());
    }

    @Test
    void provider_supportsOnlyApiKeyTokens() {
        assertThat(provider.supports(ApiKeyAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(TestingAuthenticationToken.class)).isFalse();
    }

    private static MockHttpServletRequest request(String header, String value) {
        var request = new MockHttpServletRequest();
        request.addHeader(header, value);
        return request;
    }

    private static ApiClientKeyEntity key(boolean revoked, Instant expiresAt) {
        var client = new ApiClientEntity();
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
        client.setName("Client");
        client.setReference("REF-1");
        client.setRoles(Set.of("ROLE_API"));
        var key = new ApiClientKeyEntity();
        key.setClient(client);
        key.setKeyId("key1");
        key.setSecretHash("hash");
        key.setRevoked(revoked);
        key.setExpiresAt(expiresAt);
        return key;
    }
}
