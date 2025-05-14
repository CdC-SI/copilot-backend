package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zas.admin.zec.backend.actions.authorize.UserStatus;

import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "uuid")
    private String uuid;

    @Column(name = "username")
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_entity_organizations",
        joinColumns = @JoinColumn(name = "user_uuid")
    )
    @Column(name = "organization")
    private List<String> organizations;

}
