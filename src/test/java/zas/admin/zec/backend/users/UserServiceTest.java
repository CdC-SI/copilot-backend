package zas.admin.zec.backend.users;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        userEntity.setOrganizations(List.of());

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
        savedUser.setOrganizations(List.of("testorg"));

        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        String uuid = userService.register("newUser", "password", List.of("testorg"));

        assertNotNull(uuid);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Register throws exception when username already exists")
    void register_throwsException_whenUsernameAlreadyExists() {
        UserEntity existingUser = new UserEntity();
        existingUser.setUsername("existingUser");

        when(userRepository.findByUsername("existingUser")).thenReturn(Optional.of(existingUser));

        assertThrows(IllegalArgumentException.class,
            () -> userService.register("existingUser", "password", List.of("testorg")));
    }

    @Test
    void register_ShouldCreateNewUser() {
        // given
        String username = "testuser";
        String password = "password";
        List<String> organizations = List.of("testorg");

        // when
        String uuid = userService.register(username, password, organizations);

        // then
        ArgumentCaptor<UserEntity> userEntityCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userEntityCaptor.capture());
        UserEntity savedUser = userEntityCaptor.getValue();

        assertEquals(username, savedUser.getUsername());
        assertTrue(passwordEncoder.matches(password, savedUser.getPassword()));
        assertEquals(organizations, savedUser.getOrganizations());
        assertEquals(List.of(Role.USER.name()), savedUser.getRoles());
        assertNotNull(savedUser.getUuid());
        assertEquals(uuid, savedUser.getUuid());
    }

    @Test
    void register_ShouldThrowException_WhenUserExists() {
        // given
        String username = "existinguser";
        String password = "password";
        List<String> organizations = List.of("testorg");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(new UserEntity()));

        // then
        assertThrows(IllegalArgumentException.class,
            () -> userService.register(username, password, organizations));
    }
}