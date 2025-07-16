package zas.admin.zec.backend.tools;

import org.mapstruct.Mapper;
import zas.admin.zec.backend.actions.askfaq.Answer;
import zas.admin.zec.backend.actions.askfaq.FAQItem;
import zas.admin.zec.backend.persistence.entity.FAQItemEntity;
import zas.admin.zec.backend.persistence.entity.PublicDocumentEntity;
import zas.admin.zec.backend.rag.PublicDocument;

@Mapper(componentModel = "spring")
public interface EntityMapper {

    FAQItem map(FAQItemEntity entity);

    Answer mapToAnswer(PublicDocumentEntity entity);

    PublicDocument map(PublicDocumentEntity entity);

}
