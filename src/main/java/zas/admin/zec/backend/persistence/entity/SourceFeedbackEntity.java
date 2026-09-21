package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "source_feedback")
public class SourceFeedbackEntity extends AbstractFeedbackEntity {

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackType feedbackType;

    @Override
    public boolean isNegative() {
        return feedbackType == FeedbackType.NEGATIVE;
    }

    public enum FeedbackType {
        POSITIVE,
        NEGATIVE
    }
}
