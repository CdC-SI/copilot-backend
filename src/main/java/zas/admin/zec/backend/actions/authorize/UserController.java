package zas.admin.zec.backend.actions.authorize;

import ch.admin.zas.common.security.users.ZasUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserProfile(String username, String firstName, String lastName, List<String> roles) {}
    public record UserResponse(String userId) {}
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/authenticated")
    public ResponseEntity<UserProfile> getUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof ZasUser zasUser) {
            return ResponseEntity.ok(getCopilotProfileFromZasUser(zasUser));
        } else {
            return ResponseEntity.ok(getCopilotProfileFromExternalUser(authentication.getName()));
        }
    }

    @PostMapping()
    public ResponseEntity<UserResponse> register(@RequestBody UserRegistration userRegistration, Authentication authentication) {
        try {
            String userId = userService.register(authentication.getName(), userRegistration);
            return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    private UserProfile getCopilotProfileFromZasUser(ZasUser zasUser) {
        try {
            return getCopilotProfile(zasUser.getUsername());
        } catch (IllegalArgumentException userHasNoAccount) {
            return new UserProfile(
                    zasUser.getUsername(),
                    zasUser.getFirstname(),
                    zasUser.getLastname(),
                    List.of()
            );
        }
    }

    private UserProfile getCopilotProfileFromExternalUser(String username) {
        try {
            return getCopilotProfile(username);
        } catch (IllegalArgumentException userHasNoAccount) {
            return new UserProfile(
                    username,
                    null,
                    null,
                    List.of()
            );
        }
    }

    private UserProfile getCopilotProfile(String username) {
        User byUsername = userService.getByUsername(username);
        return new UserProfile(
                byUsername.username(),
                byUsername.firstName(),
                byUsername.lastName(),
                byUsername.roles().stream().map(Role::name).toList()
        );
    }
}
