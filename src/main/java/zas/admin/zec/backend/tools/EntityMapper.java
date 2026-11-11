package zas.admin.zec.backend.tools;

import zas.admin.zec.backend.actions.askfaq.Answer;
import zas.admin.zec.backend.actions.askfaq.FAQItem;
import zas.admin.zec.backend.persistence.MetadataValues;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.entity.QuestionEntity;

public final class EntityMapper {
    private EntityMapper() {}
    public static FAQItem map(QuestionEntity question, DocumentEntity answer) {
        return new FAQItem(
                question.getId().toString(),
                MetadataValues.getString(question.getMetadata(), "language"),
                question.getContent(),
                MetadataValues.getString(question.getMetadata(), "url"),
                mapToAnswer(answer)
        );
    }

    public static Answer mapToAnswer(DocumentEntity answer) {
        return new Answer(
                answer.getContent(),
                MetadataValues.getString(answer.getMetadata(), "url"),
                MetadataValues.getString(answer.getMetadata(), "language")
        );
    }
}
