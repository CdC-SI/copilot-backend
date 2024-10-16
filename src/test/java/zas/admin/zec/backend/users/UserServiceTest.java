package zas.admin.zec.backend.users;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Get user by username returns user when user exists")
    void getByUsername_returnUser_whenUserExists() {
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername("testUser");
        userEntity.setPassword("encodedPassword");
        userEntity.setRoles(List.of("USER"));

        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(userEntity));

        User user = userService.getByUsername("testUser");

        assertEquals("testUser", user.username());
        assertEquals("encodedPassword", user.password());
        assertEquals(List.of(Role.USER), user.roles());
    }

    @Test
    @DisplayName("Get user by username throws exception when user does not exist")
    void getByUsername_throwsException_whenUserDoesNotExist() {
        when(userRepository.findByUsername("nonExistentUser")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.getByUsername("nonExistentUser"));
    }

    @Test
    @DisplayName("Register creates new user when username is unique")
    void register_createsNewUser_whenUsernameIsUnique() {
        when(userRepository.findByUsername("newUser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");

        UserEntity savedUser = new UserEntity();
        savedUser.setUuid(UUID.randomUUID().toString());
        savedUser.setUsername("newUser");
        savedUser.setPassword("encodedPassword");
        savedUser.setRoles(List.of("USER"));

        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        String uuid = userService.register("newUser", "password");

        assertNotNull(uuid);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Register throws exception when username already exists")
    void register_throwsException_whenUsernameAlreadyExists() {
        UserEntity existingUser = new UserEntity();
        existingUser.setUsername("existingUser");

        when(userRepository.findByUsername("existingUser")).thenReturn(Optional.of(existingUser));

        assertThrows(IllegalArgumentException.class, () -> userService.register("existingUser", "password"));
    }
}