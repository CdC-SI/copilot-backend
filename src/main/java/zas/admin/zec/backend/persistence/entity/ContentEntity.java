package zas.admin.zec.backend.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class ContentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false, length = 3)
    private String language;

    @Column(name = "user_uuid")
    private String userUuid;

    @Column
    private String url;

    @Column
    private String summary;

    @Column
    private String doctype;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "character varying[]")
    private String[] tags;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "character varying[]")
    private String[] subtopics;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "character varying[]")
    private String[] hyq;

    @Type(StringArrayType.class)
    @Column(name = "hyq_declarative", columnDefinition = "character varying[]")
    private String[] hyqDeclarative;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "character varying[]")
    private String[] organizations;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;
}
