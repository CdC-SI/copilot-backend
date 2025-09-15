package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_client")
public class ApiClientEntity {

    @Id
    @Getter
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Getter
    @Setter
    @Column(unique = true, nullable = false, length = 128)
    private String name;

    @Getter
    @Setter
    @Column(unique = true, nullable = false, length = 128)
    private String reference;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Getter
    @Setter
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_client_roles", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "role", nullable = false, length = 64)
    private Set<String> roles = new HashSet<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public enum Status {ACTIVE, INACTIVE}

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public List<SimpleGrantedAuthority> rolesAsAuthorities() {
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
