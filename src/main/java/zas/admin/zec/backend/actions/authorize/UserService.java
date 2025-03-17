package zas.admin.zec.backend.actions.authorize;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.UserEntity;
import zas.admin.zec.backend.persistence.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final String USER_NOT_FOUND = "User not found";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
                                          userEntity.getPassword(),
                                          userEntity.getRoles().stream().map(Role::from).toList(),
                                          userEntity.getOrganizations()))
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
    }

    public String register(String username, String password, List<String> organizations) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        var user = new UserEntity();
        user.setUuid(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setOrganizations(organizations);
        user.setRoles(List.of(Role.USER.name()));

        UserEntity savedUser = userRepository.save(user);
        return savedUser.getUuid();
    }
}
