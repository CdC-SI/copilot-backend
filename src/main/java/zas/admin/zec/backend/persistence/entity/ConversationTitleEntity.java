package zas.admin.zec.backend.persistence.entity;

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

    @Column(name = "chat_title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "workspace", nullable = false, columnDefinition = "text")
    private String workspace;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

}
