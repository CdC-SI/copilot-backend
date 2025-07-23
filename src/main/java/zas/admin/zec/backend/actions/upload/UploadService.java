package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.strategy.AdminDocUploadStrategyFactory;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.util.List;

@Slf4j
@Service
public class UploadService {

    private final AdminDocUploadStrategyFactory adminDocUploadStrategyFactory;
    private final TempSourceDocumentRepository sourceDocumentRepository;

    public UploadService(AdminDocUploadStrategyFactory adminDocUploadStrategyFactory,
                         TempSourceDocumentRepository sourceDocumentRepository) {

        this.adminDocUploadStrategyFactory = adminDocUploadStrategyFactory;
        this.sourceDocumentRepository = sourceDocumentRepository;
    }

    public record Doc(String filename, ByteArrayResource content) {}
    public Doc download(String filename) {
        TempSourceDocumentEntity byFileName = sourceDocumentRepository.findByFileName(filename);
        return new Doc(filename, new ByteArrayResource(byFileName.getContent()));
    }

    @Async
    public void uploadPersonalDocument(DocumentToUpload document, String userUuid) {
        try {
            log.info("Starting async {} processing for user {}", document.name(), userUuid);
            //TODO implement ETL Pipeline
            log.info("Finished async {} processing for user {}", document.name(), userUuid);
        } catch (Exception e) {
            log.error("Error processing {} upload for user {}", document.name(), userUuid, e);
        }
    }

    @Async
    public void uploadAdminDocuments(List<DocumentToUpload> documents) {
        try {
            log.info("Starting async {} admin documents", documents.size());
            documents.forEach(doc -> adminDocUploadStrategyFactory.getUploadStrategy(doc).upload(doc));
            log.info("Finished async processing admin documents");
        } catch (Exception e) {
            log.error("Error processing admin docs {}", documents, e);
        }
    }
}
