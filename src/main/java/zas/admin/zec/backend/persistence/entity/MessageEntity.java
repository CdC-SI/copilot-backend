package zas.admin.zec.backend.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.IntArrayType;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "chat_history")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_uuid", nullable = false)
    private String userId;

    @Column(name = "conversation_uuid", nullable = false)
    private String conversationId;

    @Column(name = "message_uuid", nullable = false)
    private String messageId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "message", nullable = false, length = 65_535)
    private String message;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Type(StringArrayType.class)
    @Column(name = "sources", columnDefinition = "text[]")
    private String[] sources;

    @Type(StringArrayType.class)
    @Column(name = "suggestions", columnDefinition = "text[]")
    private String[] suggestions;

    @Column(name = "faq_id")
    private UUID faqId;

    @Type(IntArrayType.class)
    @Column(name = "retrieved_docs", columnDefinition = "integer[]")
    private int[] retrievedDocs;

    /**
     * Workspace utilisé pour répondre à ce message : fourni explicitement dans la {@code Question},
     * inféré par {@code RAGTool}, ou {@code null} si aucun n'a été résolu (ex. le tool de recherche
     * documentaire n'a pas été invoqué pour ce message).
     */
    @Column(name = "workspace")
    private String workspace;
}
