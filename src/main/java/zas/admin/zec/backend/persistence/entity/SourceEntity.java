package zas.admin.zec.backend.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * Entité représentant une Source de connaissance (auparavant portée uniquement par la
 * métadonnée {@code vector_store.metadata->>'source'}).
 *
 * <p>Les <em>contenus</em> (documents / urls) d'une source ne sont pas stockés ici : ils sont
 * dérivés à la volée des métadonnées {@code title} / {@code url} des chunks de
 * {@code vector_store} dont {@code source} pointe vers {@link #name}.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "source")
public class SourceEntity {

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
