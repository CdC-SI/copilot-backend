package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.actions.upload.etl.PdfDocumentReader;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.strategy.AdminDocUploadStrategyFactory;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class UploadService {

    private final AdminDocUploadStrategyFactory adminDocUploadStrategyFactory;
    private final TempSourceDocumentRepository sourceDocumentRepository;
    private final VectorStore vectorStore;
    private final TempSourceDocumentRepository tempSourceDocumentRepository;

    public UploadService(AdminDocUploadStrategyFactory adminDocUploadStrategyFactory,
                         TempSourceDocumentRepository sourceDocumentRepository,
                         VectorStore vectorStore, TempSourceDocumentRepository tempSourceDocumentRepository) {

        this.adminDocUploadStrategyFactory = adminDocUploadStrategyFactory;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.vectorStore = vectorStore;
        this.tempSourceDocumentRepository = tempSourceDocumentRepository;
    }

    public record Doc(String filename, ByteArrayResource content) {}
    public Doc download(String filename) {
        TempSourceDocumentEntity byFileName = sourceDocumentRepository.findByFileName(filename);
        return new Doc(filename, new ByteArrayResource(byFileName.getContent()));
    }

    @Transactional
    public void uploadPersonalDocument(DocumentToUpload document, String userUuid) {
        var reader = new PdfDocumentReader(document.file());
        List<Document> documents = reader.read()
                .stream()
                .map(doc -> enrichMetadata(doc, userUuid, document.file().getOriginalFilename()))
                .toList();

        try {
            TempSourceDocumentEntity personalDoc = new TempSourceDocumentEntity();
            personalDoc.setFileName(document.file().getOriginalFilename());
            personalDoc.setContent(document.file().getBytes());
            personalDoc.setUserUuid(userUuid);
            tempSourceDocumentRepository.save(personalDoc);
            vectorStore.write(documents);
        } catch (IOException e) {
            throw new UploadException(document.file().getOriginalFilename(), "Error while uploading personal document", e);
        }
    }

    public void uploadAdminDocuments(List<DocumentToUpload> documents) {
        documents.forEach(doc -> adminDocUploadStrategyFactory.getUploadStrategy(doc).upload(doc));
    }

    private Document enrichMetadata(Document document, String userUuid, String filename) {
        //Suppression des caractères null
        //(évite les erreurs sql car postgres ne supporte pas les caractères null dans les chaînes de caractères)
        Document clean = document.mutate().text(document.getText().replaceAll("\u0000", "")).build();
        clean.getMetadata().put("user_uuid", userUuid);
        clean.getMetadata().put("title", filename);
        return clean;
    }
}
