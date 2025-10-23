package zas.admin.zec.backend.actions.upload.strategy;

import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.QuestionRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

@Component
public class AdminDocUploadStrategyFactory {

    private final TempSourceDocumentRepository sourceRepository;
    private final DocumentRepository documentRepository;
    private final QuestionRepository questionRepository;

    public AdminDocUploadStrategyFactory(TempSourceDocumentRepository sourceRepository, DocumentRepository documentRepository, QuestionRepository questionRepository) {
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.questionRepository = questionRepository;
    }

    public UploadStrategy getUploadStrategy(DocumentToUpload doc) {
        return "text/csv".equals(doc.file().getContentType())
                ? new EmbeddedDocUploadStrategy(documentRepository, questionRepository)
                : new SourceDocUploadStrategy(sourceRepository);
    }
}
