package zas.admin.zec.backend.users;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    public record UserRegistration(String username, String password) {}
    public record UserProfile(String username, List<String> roles) {}
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/authenticated")
    public ResponseEntity<UserProfile> getUser(Authentication authentication) {
        UserDetails authenticated = (UserDetails) authentication.getPrincipal();
        return ResponseEntity.ok(new UserProfile(
                authenticated.getUsername(),
                authenticated.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
        ));
    }

    @PostMapping()
    public ResponseEntity<String> register(@RequestBody UserRegistration userRegistration) {
        try {
            String userId = userService.register(userRegistration.username(), userRegistration.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(userId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

}
