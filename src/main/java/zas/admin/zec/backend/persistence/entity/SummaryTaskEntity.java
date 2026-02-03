package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zas.admin.zec.backend.actions.summarize.SummaryTaskStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "summary_tasks", indexes = {
    @Index(name = "idx_summary_tasks_navs", columnList = "navs")
})
public class SummaryTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "navs", nullable = false, length = 13)
    private String navs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SummaryTaskStatus status;

    @Column(name = "summary_markdown", columnDefinition = "TEXT")
    private String summaryMarkdown;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "summary_task_references", joinColumns = @JoinColumn(name = "summary_task_id"))
    @Column(name = "reference", columnDefinition = "TEXT")
    private List<String> references;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

