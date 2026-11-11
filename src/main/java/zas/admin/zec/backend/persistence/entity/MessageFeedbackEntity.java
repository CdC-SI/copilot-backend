package zas.admin.zec.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "chat_feedback")
public class MessageFeedbackEntity extends AbstractFeedbackEntity {

    @Column(name = "score")
    private Integer score;

    @Override
    public boolean isNegative() {
        return score != null && score < 0;
    }
}
