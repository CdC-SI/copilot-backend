package zas.admin.zec.backend.actions.authorize;

import ch.admin.zas.common.security.users.ZasUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.config.RequireAdmin;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserResponse(String userId) {}
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequireAdmin
    public ResponseEntity<Page<UserProfile>> getAllUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "sortField", defaultValue = "status") String sortField,
            @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {

        var sort = StringUtils.hasLength(sortField)
                ? Sort.by(Sort.Direction.valueOf(sortDirection), sortField)
                : Sort.unsorted();
        var pageRequest = PageRequest.of(page, pageSize, sort);
        return ResponseEntity.ok(userService.getAllUsers(pageRequest));
    }

    @RequireAdmin
    @PutMapping("/{username}/validate")
    public ResponseEntity<Void> validateUser(@PathVariable String username) {
        userService.validate(username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin
    @PutMapping("/{username}/reactivate")
    public ResponseEntity<Void> reactivateUser(@PathVariable String username) {
        userService.reactivate(username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin
    @PutMapping("/{username}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable String username) {
        userService.deactivate(username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin
    @PutMapping("/{username}/promote")
    public ResponseEntity<Void> promoteUser(@PathVariable String username) {
        userService.promote(username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin
    @PutMapping("/{username}/demote")
    public ResponseEntity<Void> demoteUser(@PathVariable String username) {
        userService.demote(username);
        return ResponseEntity.ok().build();
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
                    UserStatus.GUEST,
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
                    UserStatus.GUEST,
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
                byUsername.status(),
                byUsername.roles().stream().map(Role::name).toList()
        );
    }
}
