package zas.admin.zec.backend.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

public class ZasUser extends JwtAuthenticationToken {

    private final String firstname;
    private final String lastname;
    private final String trigramme;

    public ZasUser(Jwt jwt, Collection<? extends GrantedAuthority> authorities,
                   String username, String firstname, String lastname, String trigramme) {
        super(jwt, authorities, username);
        this.firstname = firstname;
        this.lastname = lastname;
        this.trigramme = trigramme;
    }

    public String getUsername() {
        return getName();
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getTrigramme() {
        return trigramme;
    }
}
