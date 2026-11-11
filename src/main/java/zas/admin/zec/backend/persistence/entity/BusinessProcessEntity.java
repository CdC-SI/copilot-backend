package zas.admin.zec.backend.persistence.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Processus métier interne (BPMN) versionné : une ligne par version et par langue, non chunkée.
 * Les métadonnées ({@code process_id}, {@code bpmn_id}, {@code valid_from}, {@code lang}...) sont
 * interrogées par {@code DocumentTool} ; le contenu ({@code nodes}, {@code arcs}) n'est retourné qu'à la demande.
 */
@Getter
@Setter
@Entity
@Table(name = "business_process")
public class BusinessProcessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Type(JsonType.class)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> content;

    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
