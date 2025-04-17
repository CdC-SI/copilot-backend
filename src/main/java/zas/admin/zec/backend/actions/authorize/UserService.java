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

    public User getByUsername(String username) {
        Optional<UserEntity> byUsername = userRepository.findByUsername(username);
        return byUsername
                .map(userEntity -> new User(userEntity.getUsername(),
                                          userEntity.getRoles().stream().map(Role::from).toList(),
                                          userEntity.getOrganizations()))
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
    }

    public String register(String username, List<String> organizations) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        var user = new UserEntity();
        user.setUuid(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setOrganizations(organizations == null ? List.of() : organizations);
        user.setRoles(List.of(Role.USER.name()));

        UserEntity savedUser = userRepository.save(user);
        return savedUser.getUuid();
    }
}
