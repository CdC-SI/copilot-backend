package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zas.admin.zec.backend.actions.sourcerequest.RequestStatus;

import java.time.LocalDateTime;

/**
 * Entité représentant une demande d'ajout de source au système.
 */
@Getter
@Setter
@Entity
@Table(name = "source_requests")
public class SourceRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "requester_username", nullable = false, length = 255)
    private String requesterUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private RequestStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = RequestStatus.WAITING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
