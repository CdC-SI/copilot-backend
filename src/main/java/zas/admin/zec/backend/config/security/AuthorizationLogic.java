package zas.admin.zec.backend.config.security;

import ch.admin.zas.common.security.users.ZasUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.authorize.Role;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.authorize.UserStatus;

@Component("authz")
public class AuthorizationLogic {
    private final UserService userService;

    public AuthorizationLogic(UserService userService) {
        this.userService = userService;
    }

    public boolean isUser(Authentication authentication) {
        return hasRole(authentication.getName(), Role.USER);
    }

    public boolean isZasUser(Authentication authentication) {
        return authentication instanceof ZasUser && isUser(authentication);
    }

    public boolean isInternalUser(Authentication authentication) {
        var userId = userService.getUuid(authentication.getName());
        return userService.hasAccessToInternalDocuments(userId);
    }

    public boolean isAdmin(Authentication authentication) {
        return hasRole(authentication.getName(), Role.ADMIN);
    }

    public boolean isExpert(Authentication authentication) {
        return hasAnyRole(authentication.getName(), Role.EXPERT, Role.ADMIN);
    }

    public boolean isTranslator(Authentication authentication) {
        return hasAnyRole(authentication.getName(), Role.TRANSLATOR, Role.ADMIN);
    }

    public boolean isExternalClient(Authentication authentication) {
        return authentication.getAuthorities().contains(new SimpleGrantedAuthority("EXTERNAL_CLIENT"));
    }

    private boolean hasRole(String username, Role role) {
        try {
            var copilotUser = userService.getByUsername(username);
            return UserStatus.ACTIVE.equals(copilotUser.status()) &&
                    copilotUser.roles().contains(role);
        } catch (IllegalArgumentException userNotFound) {
            return false;
        }
    }

    private boolean hasAnyRole(String username, Role... roles) {
        try {
            var copilotUser = userService.getByUsername(username);
            if (!UserStatus.ACTIVE.equals(copilotUser.status())) {
                return false;
            }
            for (var role : roles) {
                if (copilotUser.roles().contains(role)) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException userNotFound) {
            return false;
        }
    }
}
