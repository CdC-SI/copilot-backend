package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zas.admin.zec.backend.actions.converse.AttachmentStatus;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "attachment")
public class AttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_uuid", nullable = false)
    private String userId;

    @Column(name = "conversation_uuid", nullable = false)
    private String conversationId;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_bytes", nullable = false)
    private byte[] fileBytes;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttachmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code")
    private UploadErrorCode errorCode;

    @Column(name = "job_id")
    private String jobId;

    @Column(name = "job_submitted_at")
    private LocalDateTime jobSubmittedAt;

    @Column(name = "job_attempts", nullable = false)
    private int jobAttempts;

    @Column(name = "next_poll_at")
    private LocalDateTime nextPollAt;

}
