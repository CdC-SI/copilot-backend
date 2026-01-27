package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "temp_source_document")
public class TempSourceDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", unique = true)
    private String fileName;

    @Column(name = "content")
    private byte[] content;

    @Column(name = "user_uuid", nullable = false)
    private String userUuid;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

}
