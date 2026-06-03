package zas.admin.zec.backend.actions.authorize;

import zas.admin.zec.backend.config.security.ZasUser;
import ch.admin.zas.jweb.securityevents.core.utils.OPDOOperation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.config.security.RequireAdmin;
import zas.admin.zec.backend.tools.SecurityLogging;
import zas.admin.zec.backend.tools.OpdoPersonalData;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final String EPORTAL_GUEST_USERNAME = "00000000-0000-0000-0000-000000000000";

    public record UserResponse(String userId) {}
    private final UserService userService;
    private final SecurityLogging securityLogging;

    public UserController(UserService userService, SecurityLogging securityLogging) {
        this.userService = userService;
        this.securityLogging = securityLogging;
    }

    @GetMapping
    @RequireAdmin("Récupération de la liste des utilisateurs")
    public ResponseEntity<Page<UserProfile>> getAllUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "sortField", defaultValue = "status") String sortField,
            @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {

        var sort = StringUtils.hasLength(sortField)
                ? Sort.by(Sort.Direction.valueOf(sortDirection), sortField)
                : Sort.unsorted();
        var pageRequest = PageRequest.of(page, pageSize, sort);
        Page<UserProfile> users = userService.getAllUsers(pageRequest);
        var personalDataList = users.getContent().stream()
                .map(u -> OpdoPersonalData.builder().field("firstName", u.firstName()).field("lastName", u.lastName()).build())
                .toList();
        securityLogging.log("Récupération de la liste des utilisateurs", OPDOOperation.READING, personalDataList);
        return ResponseEntity.ok(users);
    }

    @RequireAdmin("Validation d un utilisateur")
    @PutMapping("/{username}/validate")
    public ResponseEntity<Void> validateUser(@PathVariable String username) {
        userService.validate(username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin("Réactivation d un utilisateur")
    @PutMapping("/{username}/reactivate")
    public ResponseEntity<Void> reactivateUser(@PathVariable String username) {
        userService.reactivate(username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin("Désactivation d un utilisateur")
    @PutMapping("/{username}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable String username) {
        userService.deactivate(username);
        securityLogging.logPrivilegeModification("modify", username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin("Modification des rôles d un utilisateur")
    @PutMapping("/{username}/roles")
    public ResponseEntity<Void> updateUserRoles(
            @PathVariable String username,
            @RequestBody @Valid UpdateRolesRequest request) {
        userService.updateRoles(username, request.roles());
        securityLogging.logPrivilegeModification("modify", username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin("Internalisation d un utilisateur")
    @PutMapping("/{username}/internalize")
    public ResponseEntity<Void> internalizeUser(@PathVariable String username) {
        userService.internalize(username);
        securityLogging.logPrivilegeModification("modify", username);
        return ResponseEntity.ok().build();
    }

    @RequireAdmin("Externalisation d un utilisateur")
    @PutMapping("/{username}/externalize")
    public ResponseEntity<Void> externalizeUser(@PathVariable String username) {
        userService.externalize(username);
        securityLogging.logPrivilegeModification("modify", username);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/authenticated")
    public ResponseEntity<UserProfile> getUser(Authentication authentication) {
        UserProfile profile;
        if (authentication.getName().equals(EPORTAL_GUEST_USERNAME)) {
            profile = getCopilotGuestProfile();
        } else if (authentication instanceof ZasUser zasUser) {
            profile = getCopilotProfileFromZasUser(zasUser);
        } else {
            profile = getCopilotProfileFromExternalUser(authentication.getName());
        }
        securityLogging.log("Accès au profil utilisateur", OPDOOperation.READING,
                List.of(OpdoPersonalData.builder().field("firstName", profile.firstName()).field("lastName", profile.lastName()).build()));
        return ResponseEntity.ok(profile);
    }

    @PostMapping()
    public ResponseEntity<UserResponse> register(@RequestBody UserRegistration userRegistration, Authentication authentication) {
        try {
            String userId = userService.register(authentication.getName(), userRegistration);
            securityLogging.log("Création d'un compte utilisateur", OPDOOperation.REGISTRATION,
                    List.of(OpdoPersonalData.builder().field("firstName", userRegistration.firstName()).field("lastName", userRegistration.lastName()).build()));
            securityLogging.logPrivilegeModification("add", authentication.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    private UserProfile getCopilotGuestProfile() {
        return new UserProfile(
                EPORTAL_GUEST_USERNAME,
                "John",
                "Doe",
                UserStatus.JOHN_DOE,
                List.of(),
                false
        );
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
                    List.of(),
                    false
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
                    List.of(),
                    false
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
                byUsername.roles().stream().map(Role::name).toList(),
                byUsername.internalUser()
        );
    }
}
