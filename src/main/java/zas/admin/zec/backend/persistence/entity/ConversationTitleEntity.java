package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zas.admin.zec.backend.actions.converse.ConversationType;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "chat_title")
public class ConversationTitleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_uuid", nullable = false)
    private String userId;

    @Column(name = "conversation_uuid", nullable = false)
    private String conversationId;

    @Column(name = "chat_title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    // Déterminé une seule fois à la création de la conversation, puis immuable.
    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false)
    private ConversationType conversationType;

}
