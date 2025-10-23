package zas.admin.zec.backend.tools;

import zas.admin.zec.backend.actions.askfaq.Answer;
import zas.admin.zec.backend.actions.askfaq.FAQItem;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.entity.QuestionEntity;

public final class EntityMapper {
    private EntityMapper() {}
    public static FAQItem map(QuestionEntity question, DocumentEntity answer) {
        return new FAQItem(
                question.getId().toString(),
                question.getMetadata().get("language"),
                question.getContent(),
                question.getMetadata().get("url"),
                mapToAnswer(answer)
        );
    }

    public static Answer mapToAnswer(DocumentEntity answer) {
        return new Answer(
                answer.getContent(),
                answer.getMetadata().get("url"),
                answer.getMetadata().get("language")
        );
    }
}
