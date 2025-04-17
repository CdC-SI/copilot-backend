package zas.admin.zec.backend.actions.authorize;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.config.RequireUser;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserRegistration(List<String> organizations) {}
    public record UserProfile(String username, List<String> roles) {}
    public record UserResponse(String userId) {}
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @RequireUser
    @GetMapping("/authenticated")
    public ResponseEntity<UserProfile> getUser(Authentication authentication) {
        User byUsername = userService.getByUsername(authentication.getName());
        return ResponseEntity.ok(new UserProfile(
                byUsername.username(),
                byUsername.roles().stream().map(Role::name).toList()
        ));
    }

    @PostMapping()
    public ResponseEntity<UserResponse> register(@RequestBody UserRegistration userRegistration, Authentication authentication) {
        try {
            String userId = userService.register(authentication.getName(), userRegistration.organizations());
            return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
