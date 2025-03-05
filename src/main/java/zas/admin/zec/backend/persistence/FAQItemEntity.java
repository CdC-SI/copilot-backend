package zas.admin.zec.backend.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "question")
public class FAQItemEntity extends EmbeddedContentEntity {

    @ManyToOne
    @JoinColumn(name = "source_id", nullable = false)
    private SourceEntity source;

    @ManyToOne
    @JoinColumn(name = "answer_id", nullable = false)
    private DocumentEntity answer;

}
