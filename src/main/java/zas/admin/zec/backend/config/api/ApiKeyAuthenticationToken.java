package zas.admin.zec.backend.config.api;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

// Avant validation: principal = keyId (String), credentials = rawSecret (String)
// Après validation: principal = ApiClientPrincipal (avec id, name, etc.), credentials = null
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private final Object credentials;

    public ApiKeyAuthenticationToken(String keyId, String rawSecret) {
        super(null);
        this.principal = keyId;
        this.credentials = rawSecret;
        setAuthenticated(false);
    }

    public ApiKeyAuthenticationToken(Object principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = null;
        super.setAuthenticated(true);
    }

    @Override public Object getCredentials() { return credentials; }
    @Override public Object getPrincipal() { return principal; }

    public String keyId() { return (String) principal; }
    public String rawSecret() { return (String) credentials; }
}
