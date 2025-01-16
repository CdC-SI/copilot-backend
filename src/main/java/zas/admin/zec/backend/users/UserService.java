package zas.admin.zec.backend.users;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String getUuid(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getUuid();
    }

    public List<String> getOrganizations(String username) {
        List<String> orgs = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getOrganizations();
        return orgs;
    }

    public User getByUsername(String username) {
        Optional<UserEntity> byUsername = userRepository.findByUsername(username);
        return byUsername
                .map(userEntity -> new User(userEntity.getUsername(),
                                          userEntity.getPassword(),
                                          userEntity.getRoles().stream().map(Role::from).toList(),
                                          userEntity.getOrganizations()))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
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
