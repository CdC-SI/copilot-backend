package zas.admin.zec.backend.persistence;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
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
    @Column(columnDefinition = "text[]")
    private String[] tags;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] subtopics;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] hyq;

    @Type(StringArrayType.class)
    @Column(name = "hyq_declarative", columnDefinition = "text[]")
    private String[] hyqDeclarative;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] organizations;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;
}
