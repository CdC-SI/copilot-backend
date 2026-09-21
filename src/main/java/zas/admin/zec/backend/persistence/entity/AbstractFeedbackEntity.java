package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Colonnes communes aux feedbacks de message ({@link MessageFeedbackEntity}) et de source
 * ({@link SourceFeedbackEntity}). Chaque sous-classe conserve sa propre table.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false)
    private String userUuid;

    @Column(name = "conversation_uuid", nullable = false)
    private String conversationUuid;

    @Column(name = "message_uuid", nullable = false)
    private String messageUuid;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "question", columnDefinition = "text")
    private String question;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FeedbackStatus status = FeedbackStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private FeedbackCategory category;

    /**
     * Indique si ce feedback exprime un avis négatif, utilisé notamment pour déclencher
     * la notification des modérateurs.
     */
    public abstract boolean isNegative();
}
