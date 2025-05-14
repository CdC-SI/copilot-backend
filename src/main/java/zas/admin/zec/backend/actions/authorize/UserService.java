package zas.admin.zec.backend.actions.authorize;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.entity.UserEntity;
import zas.admin.zec.backend.persistence.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class UserService {

    private static final String USER_NOT_FOUND = "User not found";
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<UserProfile> getAllUsers(PageRequest pageRequest) {
        Page<UserEntity> users = userRepository.findAll(pageRequest);
        return users.map(entity -> new UserProfile(
                entity.getUsername(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getStatus(),
                entity.getRoles()));
    }

    public String getUuid(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND))
                .getUuid();

    }

    public List<String> getOrganizations(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
        return user.getOrganizations();
    }

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public User getByUsername(String username) {
        Optional<UserEntity> byUsername = userRepository.findByUsername(username);
        return byUsername
                .map(entity -> new User(
                        entity.getUsername(),
                        entity.getFirstName(),
                        entity.getLastName(),
                        entity.getStatus(),
                        entity.getRoles().stream().map(Role::from).toList(),
                        entity.getOrganizations()))
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
    }

    public String register(String username, UserRegistration registration) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        var user = new UserEntity();
        user.setUuid(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setFirstName(registration.firstName());
        user.setLastName(registration.lastName());
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setOrganizations(registration.organizations() == null ? List.of() : registration.organizations());
        user.setRoles(List.of(Role.USER.name()));

        UserEntity savedUser = userRepository.save(user);
        return savedUser.getUuid();
    }

    public void validate(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
            log.warn("User {} is not pending activation, skip validation", user.getUsername());
            return;
        }

        log.debug("Validating {}", user.getUsername());
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    public void reactivate(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.INACTIVE) {
            log.warn("User {} is not inactive, skip reactivation", user.getUsername());
            return;
        }

        log.debug("Reactivating {}", user.getUsername());
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    public void deactivate(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));

        log.debug("Deactivating {}", user.getUsername());
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }

    public void promote(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));

        if (user.getRoles().contains(Role.ADMIN.name())) {
            log.warn("User {} is already an admin, skip promotion", user.getUsername());
            return;
        }

        log.debug("Promoting {}", user.getUsername());
        user.getRoles().add(Role.ADMIN.name());
        userRepository.save(user);
    }

    public void demote(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));

        if (!user.getRoles().contains(Role.ADMIN.name())) {
            log.warn("User {} is not an admin, skip demotion", user.getUsername());
            return;
        }

        log.debug("Demoting {}", user.getUsername());
        user.getRoles().remove(Role.ADMIN.name());
        userRepository.save(user);
    }
}
