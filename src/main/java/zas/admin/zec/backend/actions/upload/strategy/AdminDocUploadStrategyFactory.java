package zas.admin.zec.backend.actions.upload.strategy;

import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.persistence.repository.InternalDocumentRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

@Component
public class AdminDocUploadStrategyFactory {

    private final TempSourceDocumentRepository sourceRepository;
    private final InternalDocumentRepository internalDocumentRepository;

    public AdminDocUploadStrategyFactory(TempSourceDocumentRepository sourceRepository, InternalDocumentRepository internalDocumentRepository) {
        this.sourceRepository = sourceRepository;
        this.internalDocumentRepository = internalDocumentRepository;
    }

    public UploadStrategy getUploadStrategy(DocumentToUpload doc) {
        return "text/csv".equals(doc.file().getContentType())
                ? new EmbeddedDocUploadStrategy(internalDocumentRepository)
                : new SourceDocUploadStrategy(sourceRepository);
    }
}
