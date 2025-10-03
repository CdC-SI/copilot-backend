package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "source_feedback")
public class SourceFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false)
    private String userId;

    @Column(name = "conversation_uuid", nullable = false)
    private String conversationId;

    @Column(name = "message_uuid", nullable = false)
    private String messageId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackType feedbackType;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    public enum FeedbackType {
        POSITIVE,
        NEGATIVE
    }
}
