package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_client_key", indexes = @Index(name = "idx_key_keyid", columnList = "keyId", unique = true))
public class ApiClientKeyEntity {

    @Id
    @Getter
    @GeneratedValue
    private UUID id;

    @Getter
    @Setter
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ApiClientEntity client;

    @Getter
    @Setter
    @Column(nullable = false, length = 64)
    private String keyId;

    @Getter
    @Setter
    @Column(nullable = false, length = 255)
    private String secretHash; // Argon2 du secret

    @Getter
    @Setter
    @Column(nullable = false)
    private boolean revoked = false;

    @Getter
    @Setter
    @Column
    private Instant expiresAt;

    @Column
    private Instant createdAt = Instant.now();

    @Column
    private Instant lastUsedAt;

    @Setter
    @Column(length = 128)
    private String label;

    public void touchLastUsed() { this.lastUsedAt = Instant.now(); }
}
