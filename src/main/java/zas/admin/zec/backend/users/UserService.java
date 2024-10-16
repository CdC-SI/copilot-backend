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

    public User getByUsername(String username) {
        Optional<UserEntity> byUsername = userRepository.findByUsername(username);
        return byUsername
                .map(userEntity -> new User(userEntity.getUsername(), userEntity.getPassword(), userEntity.getRoles().stream().map(Role::from).toList()))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public String register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        var user = new UserEntity();
        user.setUuid(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoles(List.of(Role.USER.name()));

        return userRepository.save(user).getUuid();
    }
}
