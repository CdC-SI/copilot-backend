package zas.admin.zec.backend.tools;

import org.mapstruct.Mapper;
import zas.admin.zec.backend.actions.askfaq.Answer;
import zas.admin.zec.backend.actions.askfaq.FAQItem;
import zas.admin.zec.backend.persistence.DocumentEntity;
import zas.admin.zec.backend.persistence.FAQItemEntity;

@Mapper(componentModel = "spring")
public interface FAQItemMapper {

    FAQItem map(FAQItemEntity entity);

    Answer map(DocumentEntity entity);

}
