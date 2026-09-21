package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "feedback_report_config")
public class FeedbackReportConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "recipients", nullable = false, columnDefinition = "text")
    private String recipients;

    @Column(name = "lookback_days", nullable = false)
    private Integer lookbackDays;
}
