package zas.admin.zec.backend.actions.upload.strategy;

import lombok.extern.slf4j.Slf4j;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

@Slf4j
public final class SourceDocUploadStrategy implements UploadStrategy {

    private final TempSourceDocumentRepository sourceRepository;

    public SourceDocUploadStrategy(TempSourceDocumentRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @Override
    public void upload(DocumentToUpload document) {
        var doc = new TempSourceDocumentEntity();
        doc.setFileName(document.name());
        doc.setContent(document.content());

        sourceRepository.save(doc);
    }
}
