package zas.admin.zec.backend.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.UserEntity;
import zas.admin.zec.backend.persistence.UserRepository;

import java.util.Optional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<UserEntity> byUsername = userRepository.findByUsername(username);
        if (byUsername.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }

        UserEntity userEntity = byUsername.get();
        return new User(
            userEntity.getUsername(),
            userEntity.getPassword(),
            userEntity.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .toList()
        );
    }
}
