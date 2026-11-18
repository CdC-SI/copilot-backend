package zas.admin.zec.backend.config.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import reactor.util.context.Context;

/**
 * Jeton Blue de l'utilisateur courant, transmis aux services internes appelés en son nom (zia-rag, T2). Il circule
 * dans le contexte Reactor du flux de réponse : les tools s'exécutent sur d'autres threads, hors du
 * {@code SecurityContext}.
 */
public final class UserToken {

    /** Clé du contexte Reactor portant la valeur d'en-tête ({@code Bearer <jwt>}). */
    public static final Class<UserToken> CONTEXT_KEY = UserToken.class;

    private UserToken() {
    }

    /** Valeur d'en-tête {@code Bearer <jwt>} de l'utilisateur authentifié, {@code null} hors authentification JWT. */
    public static String bearer(Authentication authentication) {
        if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> token && token.getToken() != null) {
            return "Bearer " + token.getToken().getTokenValue();
        }
        return null;
    }

    /** Ajoute le jeton au contexte Reactor s'il est connu. */
    public static Context with(Context context, String bearer) {
        return bearer == null ? context : context.put(CONTEXT_KEY, bearer);
    }
}
