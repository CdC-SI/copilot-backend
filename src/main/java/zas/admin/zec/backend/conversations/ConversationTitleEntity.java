package zas.admin.zec.backend.conversations;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "chat_title", nullable = false)
    private String title;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

}
