package zas.admin.zec.backend.actions.upload.strategy;

import lombok.extern.slf4j.Slf4j;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
public final class SourceDocUploadStrategy implements UploadStrategy {

    private final TempSourceDocumentRepository sourceRepository;

    public SourceDocUploadStrategy(TempSourceDocumentRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @Override
    public void upload(DocumentToUpload document) {
        try {
            var doc = new TempSourceDocumentEntity();
            doc.setFileName(document.file().getOriginalFilename());
            doc.setContent(document.file().getBytes());
            doc.setUploadedAt(LocalDateTime.now());
            doc.setStatus(EmbeddingStatus.PROCESSED);

            sourceRepository.save(doc);
        } catch (IOException e) {
            throw new UploadException(document.file().getOriginalFilename(), "Error while uploading PDF", e);
        }
    }
}
