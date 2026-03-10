package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.actions.upload.model.PersonalDoc;
import zas.admin.zec.backend.actions.upload.strategy.AdminDocUploadStrategyFactory;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class UploadService {

    private final AdminDocUploadStrategyFactory adminDocUploadStrategyFactory;
    private final TempSourceDocumentRepository sourceDocumentRepository;
    private final VectorStore vectorStore;
    private final TempSourceDocumentRepository tempSourceDocumentRepository;
    private final UploadAsyncProcessor uploadAsyncProcessor;

    public UploadService(AdminDocUploadStrategyFactory adminDocUploadStrategyFactory,
                         TempSourceDocumentRepository sourceDocumentRepository,
                         VectorStore vectorStore,
                         TempSourceDocumentRepository tempSourceDocumentRepository,
                         UploadAsyncProcessor uploadAsyncProcessor) {

        this.adminDocUploadStrategyFactory = adminDocUploadStrategyFactory;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.vectorStore = vectorStore;
        this.tempSourceDocumentRepository = tempSourceDocumentRepository;
        this.uploadAsyncProcessor = uploadAsyncProcessor;
    }

    public record Doc(String filename, ByteArrayResource content) {}
    public Doc download(String filename) {
        TempSourceDocumentEntity byFileName = sourceDocumentRepository.findByFileName(filename)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + filename));

        return new Doc(filename, new ByteArrayResource(byFileName.getContent()));
    }

    @Transactional
    public void uploadPersonalDocument(DocumentToUpload document, String userUuid) {
        try {
            TempSourceDocumentEntity personalDoc = new TempSourceDocumentEntity();
            personalDoc.setFileName(document.file().getOriginalFilename());
            personalDoc.setContent(document.file().getBytes());
            personalDoc.setUserUuid(userUuid);
            personalDoc.setUploadedAt(LocalDateTime.now());
            personalDoc.setStatus(EmbeddingStatus.PENDING);

            var savedDoc = tempSourceDocumentRepository.save(personalDoc);

            log.info("Document personnel {} persisté, lancement du traitement d'embedding asynchrone",
                    document.file().getOriginalFilename());

            uploadAsyncProcessor.processEmbedding(savedDoc.getId());
        } catch (IOException e) {
            throw new UploadException(document.file().getOriginalFilename(),
                    "Error while uploading personal document", e);
        }
    }

    public List<PersonalDoc> getUserPersonalDocs(String userUuid) {
        return tempSourceDocumentRepository.findAllByUserUuid(userUuid)
                .stream()
                .map(doc -> new PersonalDoc(doc.getFileName(), doc.getUploadedAt(), doc.getStatus()))
                .toList();
    }

    @Transactional
    public void deleteUserPersonalDocument(String filename, String userUuid) {
        var docEntity = tempSourceDocumentRepository.findByFileNameAndUserUuid(filename, userUuid)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Document %s not found", filename)));

        tempSourceDocumentRepository.delete(docEntity);
        vectorStore.delete(buildUserFileFilter(userUuid, filename));
    }

    public void uploadAdminDocuments(List<DocumentToUpload> documents) {
        documents.forEach(doc -> adminDocUploadStrategyFactory.getUploadStrategy(doc).upload(doc));
    }


    private Filter.Expression buildUserFileFilter(String userUuid, String filename) {
        var expressionBuilder = new FilterExpressionBuilder();
        return expressionBuilder.and(
                expressionBuilder.eq("user_uuid", userUuid),
                expressionBuilder.eq("title", filename)
        ).build();
    }
}
