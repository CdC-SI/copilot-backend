package zas.admin.zec.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@MappedSuperclass
public class EmbeddedContentEntity extends ContentEntity {

    @Array(length = 1536)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "text_embedding")
    private float[] textEmbedding;

    @Array(length = 1536)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "tags_embedding")
    private float[] tagsEmbedding;

    @Array(length = 1536)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "subtopics_embedding")
    private float[] subtopicsEmbedding;

    @Array(length = 1536)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "hyq_embedding")
    private float[] hyqEmbedding;

    @Array(length = 1536)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "hyq_declarative_embedding")
    private float[] hyqDeclarativeEmbedding;

    @Array(length = 1536)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "summary_embedding")
    private float[] summaryEmbedding;

}
