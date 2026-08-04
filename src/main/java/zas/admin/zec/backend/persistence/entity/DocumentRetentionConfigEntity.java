package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "document_retention_config")
public class DocumentRetentionConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "days_before_archival", nullable = false)
    private Integer daysBeforeArchival;

    @Column(name = "days_before_deletion", nullable = false)
    private Integer daysBeforeDeletion;

}
