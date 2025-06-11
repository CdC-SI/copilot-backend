package zas.admin.zec.backend.config;

import org.springframework.security.core.Authentication;
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

    public boolean isInternalUser(Authentication authentication) {
        var userId = userService.getUuid(authentication.getName());
        return userService.hasAccessToInternalDocuments(userId);
    }

    public boolean isAdmin(Authentication authentication) {
        return hasRole(authentication.getName(), Role.ADMIN);
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
}
