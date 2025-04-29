package zas.admin.zec.backend.tools;

import org.mapstruct.Mapper;
import zas.admin.zec.backend.actions.askfaq.Answer;
import zas.admin.zec.backend.actions.askfaq.FAQItem;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.entity.FAQItemEntity;
import zas.admin.zec.backend.rag.Document;

@Mapper(componentModel = "spring")
public interface EntityMapper {

    FAQItem map(FAQItemEntity entity);

    Answer mapToAnswer(DocumentEntity entity);

    Document map(DocumentEntity entity);

}
