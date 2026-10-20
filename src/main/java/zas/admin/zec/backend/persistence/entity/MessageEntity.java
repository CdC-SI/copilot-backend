package zas.admin.zec.backend.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    /**
     * Trace du tour (cf. {@code TurnTrace}) : statut déclaré par l'agent (answer / followup / refusal)
     * et documents consultés (RAGTool + DocumentTool), sous forme compacte. Renseigné aussi pour les
     * questions de suivi, dont les sources ne sont pas affichées, afin de les réinjecter au tour suivant
     * et de les afficher avec la réponse finale. {@code null} pour les messages utilisateur et l'historique.
     */
    @Type(JsonType.class)
    @Column(name = "consulted_sources", columnDefinition = "jsonb")
    private Map<String, Object> consultedSources;

    /**
     * Appels de tools du tour (cf. {@code ToolCallRecord}) : tool, arguments fournis par le LLM, statut, durée,
     * documents retournés. Écrit pour le débogage et les évaluations, jamais relu par l'application.
     */
    @Type(JsonType.class)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private List<Map<String, Object>> toolCalls;

    /** Tokens consommés par le tour (cf. {@code TurnUsage}). Écrit pour les évaluations, jamais relu. */
    @Type(JsonType.class)
    @Column(name = "usage", columnDefinition = "jsonb")
    private Map<String, Object> usage;

    /**
     * Workspace utilisé pour répondre à ce message : fourni explicitement dans la {@code Question},
     * inféré par {@code RAGTool}, ou {@code null} si aucun n'a été résolu (ex. le tool de recherche
     * documentaire n'a pas été invoqué pour ce message).
     */
    @Column(name = "workspace")
    private String workspace;
}
