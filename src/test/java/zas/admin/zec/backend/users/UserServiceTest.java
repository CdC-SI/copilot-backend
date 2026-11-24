package zas.admin.zec.backend.users;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import zas.admin.zec.backend.actions.authorize.Role;
import zas.admin.zec.backend.actions.authorize.User;
import zas.admin.zec.backend.actions.authorize.UserProfile;
import zas.admin.zec.backend.actions.authorize.UserRegistration;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.authorize.UserStatus;
import zas.admin.zec.backend.persistence.entity.UserEntity;
import zas.admin.zec.backend.persistence.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Get user by username returns user when user exists")
    void getByUsername_returnUser_whenUserExists() {
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername("testUser");
        userEntity.setRoles(List.of("USER"));
        userEntity.setOrganizations(List.of());

        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(userEntity));

        User user = userService.getByUsername("testUser");

        assertEquals("testUser", user.username());
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

        UserEntity savedUser = new UserEntity();
        savedUser.setUuid(UUID.randomUUID().toString());
        savedUser.setUsername("newUser");
        savedUser.setRoles(List.of("USER"));
        savedUser.setOrganizations(List.of("testorg"));

        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        String uuid = userService.register("newUser", new UserRegistration("", "", List.of("testorg")));

        assertNotNull(uuid);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Register throws exception when username already exists")
    void register_throwsException_whenUsernameAlreadyExists() {
        UserEntity existingUser = new UserEntity();
        existingUser.setUsername("existingUser");
        UserRegistration userRegistration = new UserRegistration("", "", List.of("testorg"));

        when(userRepository.findByUsername("existingUser")).thenReturn(Optional.of(existingUser));

        assertThrows(IllegalArgumentException.class,
            () -> userService.register("existingUser", userRegistration));
    }

    @Test
    void register_ShouldThrowException_WhenUserExists() {
        // given
        String username = "existinguser";
        List<String> organizations = List.of("testorg");
        UserRegistration userRegistration = new UserRegistration("", "", organizations);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(new UserEntity()));

        // then
        assertThrows(IllegalArgumentException.class,
            () -> userService.register(username, userRegistration));
    }

    @Test
    @DisplayName("Register stores a pending USER, internal when the organizations contain ZAS")
    void register_storesPendingUser_internalWhenZas() {
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        String uuid = userService.register("jdoe", new UserRegistration("John", "Doe", List.of("ZAS")));

        var captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(uuid, saved.getUuid());
        assertEquals("John", saved.getFirstName());
        assertEquals(UserStatus.PENDING_ACTIVATION, saved.getStatus());
        assertEquals(List.of("USER"), saved.getRoles());
        assertTrue(saved.isInternalUser());
    }

    @Test
    @DisplayName("Register without organizations stores an external user with no organization")
    void register_withoutOrganizations_storesExternalUser() {
        when(userRepository.findByUsername("ext")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        userService.register("ext", new UserRegistration("A", "B", null));

        var captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals(List.of(), captor.getValue().getOrganizations());
        assertFalse(captor.getValue().isInternalUser());
    }

    @Test
    @DisplayName("getAllUsers maps the page of entities to profiles")
    void getAllUsers_mapsEntitiesToProfiles() {
        var entity = user("jdoe", UserStatus.ACTIVE, new ArrayList<>(List.of("ZAS")));
        entity.setInternalUser(true);
        var pageRequest = PageRequest.of(0, 10);
        when(userRepository.findAll(pageRequest)).thenReturn(new PageImpl<>(List.of(entity), pageRequest, 1));

        var page = userService.getAllUsers(pageRequest);

        assertEquals(1, page.getTotalElements());
        assertEquals(new UserProfile("jdoe", "First", "Last", UserStatus.ACTIVE, List.of("USER"), true), page.getContent().getFirst());
    }

    @Test
    @DisplayName("getUuid, getOrganizations and existsByUsername read the user by username")
    void lookups_readUserByUsername() {
        var entity = user("jdoe", UserStatus.ACTIVE, List.of("ZAS"));
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertEquals("uuid-jdoe", userService.getUuid("jdoe"));
        assertEquals(List.of("ZAS"), userService.getOrganizations("jdoe"));
        assertTrue(userService.existsByUsername("jdoe"));
        assertFalse(userService.existsByUsername("ghost"));
        assertThrows(IllegalArgumentException.class, () -> userService.getUuid("ghost"));
        assertThrows(IllegalArgumentException.class, () -> userService.getOrganizations("ghost"));
    }

    @Test
    @DisplayName("validate activates only pending users")
    void validate_activatesOnlyPendingUsers() {
        var pending = user("p", UserStatus.PENDING_ACTIVATION, List.of());
        var inactive = user("i", UserStatus.INACTIVE, List.of());
        when(userRepository.findByUsername("p")).thenReturn(Optional.of(pending));
        when(userRepository.findByUsername("i")).thenReturn(Optional.of(inactive));

        userService.validate("p");
        userService.validate("i");

        assertEquals(UserStatus.ACTIVE, pending.getStatus());
        assertEquals(UserStatus.INACTIVE, inactive.getStatus());
        verify(userRepository).save(pending);
        verify(userRepository, never()).save(inactive);
    }

    @Test
    @DisplayName("reactivate activates only inactive users")
    void reactivate_activatesOnlyInactiveUsers() {
        var inactive = user("i", UserStatus.INACTIVE, List.of());
        var pending = user("p", UserStatus.PENDING_ACTIVATION, List.of());
        when(userRepository.findByUsername("i")).thenReturn(Optional.of(inactive));
        when(userRepository.findByUsername("p")).thenReturn(Optional.of(pending));

        userService.reactivate("i");
        userService.reactivate("p");

        assertEquals(UserStatus.ACTIVE, inactive.getStatus());
        assertEquals(UserStatus.PENDING_ACTIVATION, pending.getStatus());
        verify(userRepository).save(inactive);
        verify(userRepository, never()).save(pending);
    }

    @Test
    @DisplayName("deactivate sets the user inactive whatever its status")
    void deactivate_setsInactive() {
        var active = user("a", UserStatus.ACTIVE, List.of());
        when(userRepository.findByUsername("a")).thenReturn(Optional.of(active));

        userService.deactivate("a");

        assertEquals(UserStatus.INACTIVE, active.getStatus());
        verify(userRepository).save(active);
    }

    @Test
    @DisplayName("User state changes on an unknown user throw")
    void stateChanges_unknownUser_throw() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.validate("ghost"));
        assertThrows(IllegalArgumentException.class, () -> userService.reactivate("ghost"));
        assertThrows(IllegalArgumentException.class, () -> userService.deactivate("ghost"));
        assertThrows(IllegalArgumentException.class, () -> userService.updateRoles("ghost", List.of("USER")));
        assertThrows(IllegalArgumentException.class, () -> userService.internalize("ghost"));
        assertThrows(IllegalArgumentException.class, () -> userService.externalize("ghost"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRoles replaces the roles when all are valid")
    void updateRoles_replacesValidRoles() {
        var entity = user("jdoe", UserStatus.ACTIVE, List.of());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(entity));

        userService.updateRoles("jdoe", List.of("ADMIN", "EXPERT", "TRANSLATOR", "USER"));

        assertEquals(List.of("ADMIN", "EXPERT", "TRANSLATOR", "USER"), entity.getRoles());
        verify(userRepository).save(entity);
    }

    @Test
    @DisplayName("updateRoles rejects an unknown role without saving")
    void updateRoles_rejectsUnknownRole() {
        var entity = user("jdoe", UserStatus.ACTIVE, List.of());
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(entity));

        var error = assertThrows(IllegalArgumentException.class, () -> userService.updateRoles("jdoe", List.of("USER", "ROOT")));

        assertEquals("Invalid role: ROOT", error.getMessage());
        assertEquals(List.of("USER"), entity.getRoles());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("internalize marks the user internal and adds ZAS once")
    void internalize_addsZasOnce() {
        var external = user("e", UserStatus.ACTIVE, new ArrayList<>(List.of("OAI")));
        var alreadyZas = user("z", UserStatus.ACTIVE, new ArrayList<>(List.of("ZAS")));
        when(userRepository.findByUsername("e")).thenReturn(Optional.of(external));
        when(userRepository.findByUsername("z")).thenReturn(Optional.of(alreadyZas));

        userService.internalize("e");
        userService.internalize("z");

        assertTrue(external.isInternalUser());
        assertEquals(List.of("OAI", "ZAS"), external.getOrganizations());
        assertEquals(List.of("ZAS"), alreadyZas.getOrganizations());
    }

    @Test
    @DisplayName("externalize marks the user external and removes ZAS")
    void externalize_removesZas() {
        var internal = user("i", UserStatus.ACTIVE, new ArrayList<>(List.of("ZAS", "OAI")));
        internal.setInternalUser(true);
        when(userRepository.findByUsername("i")).thenReturn(Optional.of(internal));

        userService.externalize("i");

        assertFalse(internal.isInternalUser());
        assertEquals(List.of("OAI"), internal.getOrganizations());
        verify(userRepository).save(internal);
    }

    @Test
    @DisplayName("hasAccessToInternalDocuments delegates to the internal flag of the user")
    void hasAccessToInternalDocuments_checksInternalFlag() {
        when(userRepository.existsByUuidAndInternalUser("u1", true)).thenReturn(true);

        assertTrue(userService.hasAccessToInternalDocuments("u1"));
        assertFalse(userService.hasAccessToInternalDocuments("u2"));
    }

    private static UserEntity user(String username, UserStatus status, List<String> organizations) {
        var entity = new UserEntity();
        entity.setUuid("uuid-" + username);
        entity.setUsername(username);
        entity.setFirstName("First");
        entity.setLastName("Last");
        entity.setStatus(status);
        entity.setRoles(List.of("USER"));
        entity.setOrganizations(organizations);
        return entity;
    }
}