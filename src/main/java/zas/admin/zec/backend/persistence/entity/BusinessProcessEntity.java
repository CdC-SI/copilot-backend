package zas.admin.zec.backend.persistence.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.UUID;

/**
 * Processus métier BPMN. Le JSON complet (nom, {@code bpanda_id}, validité, état, nœuds, arcs) est
 * stocké tel quel dans {@code content} ; seul {@code bpmn_id} est extrait en colonne.
 */
@Getter
@Setter
@Entity
@Table(name = "business_process")
public class BusinessProcessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bpmn_id", nullable = false, unique = true)
    private String bpmnId;

    @Type(JsonType.class)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private String content;
}
