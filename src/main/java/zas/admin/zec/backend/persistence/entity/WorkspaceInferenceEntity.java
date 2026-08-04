package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Journalise une inférence de workspace réalisée par {@code RAGTool} (conversations COMPLETE) et,
 * le cas échéant, la correction ultérieure apportée par l'utilisateur sur la même question.
 */
@Getter
@Setter
@Entity
@Table(name = "workspace_inference")
public class WorkspaceInferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_uuid")
    private String userUuid;

    @Column(name = "conversation_uuid")
    private String conversationUuid;

    @Column(name = "question", columnDefinition = "text")
    private String question;

    @Column(name = "inferred_workspace")
    private String inferredWorkspace;

    @Column(name = "corrected_workspace")
    private String correctedWorkspace;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
