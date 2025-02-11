package zas.admin.zec.backend.users;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.ArrayList;

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

    @Column(name = "password")
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_entity_organizations",
        joinColumns = @JoinColumn(name = "user_uuid")
    )
    @Column(name = "organization")
    private List<String> organizations = new ArrayList<>();

}
