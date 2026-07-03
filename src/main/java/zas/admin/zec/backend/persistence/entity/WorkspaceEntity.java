package zas.admin.zec.backend.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Entité représentant un Workspace : un ensemble de {@link SourceEntity} accompagné d'un résumé
 * et des questions / thématiques auxquelles il peut répondre.
 */
@Getter
@Setter
@Entity
@Table(name = "workspace")
public class WorkspaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Type(StringArrayType.class)
    @Column(name = "hypothetical_questions", columnDefinition = "text[]")
    private String[] hypotheticalQuestions;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "workspace_source",
            joinColumns = @JoinColumn(name = "workspace_id"),
            inverseJoinColumns = @JoinColumn(name = "source_id")
    )
    private Set<SourceEntity> sources = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (hypotheticalQuestions == null) {
            hypotheticalQuestions = new String[0];
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (hypotheticalQuestions == null) {
            hypotheticalQuestions = new String[0];
        }
    }
}
