package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import zas.admin.zec.backend.actions.alert.AlertLevel;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "alerts")
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text_fr", nullable = false)
    private String textFr;

    @Column(name = "text_de", nullable = false)
    private String textDe;

    @Column(name = "text_it", nullable = false)
    private String textIt;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private AlertLevel level;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
