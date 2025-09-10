package zas.admin.zec.backend.persistence.entity.tariff;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "positions")
public class PositionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "subchapter_id", nullable = false)
    private SubChapterEntity subchapter;

    @Column(name = "node_type")
    private String nodeType;

    @Column(name = "parent_node_type")
    private String parentNodeType;

    @Column(name = "parent")
    private String parent;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    @Column(name = "summary")
    private String summary;

    @Column(name = "fulltext", columnDefinition = "text")
    private String fulltext;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
