package zas.admin.zec.backend.actions.authorize;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.entity.UserEntity;
import zas.admin.zec.backend.persistence.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final String USER_NOT_FOUND = "User not found";
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        user.setOrganizations(registration.organizations() == null ? List.of() : registration.organizations());
        user.setRoles(List.of(Role.USER.name()));

        UserEntity savedUser = userRepository.save(user);
        return savedUser.getUuid();
    }
}
