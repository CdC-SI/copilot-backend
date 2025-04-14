package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "chat_feedback")
public class MessageFeedbackEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_uuid")
    private String userUuid;

    @Column(name = "conversation_uuid")
    private String conversationUuid;

    @Column(name = "message_uuid")
    private String messageUuid;

    @Column(name = "score")
    private Integer score;

    @Column(name = "comment")
    private String comment;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
