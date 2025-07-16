package zas.admin.zec.backend.actions.upload.strategy;

import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

@Component
public class AdminDocUploadStrategyFactory {

    private final TempSourceDocumentRepository sourceRepository;
    private final DocumentRepository documentRepository;

    public AdminDocUploadStrategyFactory(TempSourceDocumentRepository sourceRepository, DocumentRepository documentRepository) {
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
    }

    public UploadStrategy getUploadStrategy(DocumentToUpload doc) {
        return "text/csv".equals(doc.contentType())
                ? new EmbeddedDocUploadStrategy(documentRepository)
                : new SourceDocUploadStrategy(sourceRepository);
    }
}
