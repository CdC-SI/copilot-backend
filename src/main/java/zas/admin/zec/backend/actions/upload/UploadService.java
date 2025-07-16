package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import zas.admin.zec.backend.actions.upload.model.DocumentChunk;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.strategy.AdminDocUploadStrategyFactory;
import zas.admin.zec.backend.persistence.entity.PublicDocumentEntity;
import zas.admin.zec.backend.persistence.entity.SourceEntity;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.SourceRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class UploadService {

    private static final String UPLOAD_SOURCE = "user_pdf_upload:";
    private final WebClient pyBackendWebClient;
    private final SourceRepository sourceRepository;
    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final AdminDocUploadStrategyFactory adminDocUploadStrategyFactory;
    private final TempSourceDocumentRepository sourceDocumentRepository;

    public UploadService(WebClient pyBackendWebClient, SourceRepository sourceRepository,
                         DocumentRepository documentRepository, EmbeddingModel embeddingModel,
                         AdminDocUploadStrategyFactory adminDocUploadStrategyFactory,
                         TempSourceDocumentRepository sourceDocumentRepository) {

        this.pyBackendWebClient = pyBackendWebClient;
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
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
            var chunks = parseAndChunkDocument(document)
                    .stream()
                    .map(chunk -> enrichAfterParsing(document, chunk, userUuid))
                    .toList();
            uploadChunks(document, chunks);
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

    private void uploadChunks(DocumentToUpload document, List<DocumentChunk> chunks) {
        var documentSource = UPLOAD_SOURCE + document.name();
        var source = sourceRepository.findByUrl(documentSource)
                .orElseGet(() -> sourceRepository.save(new SourceEntity(documentSource)));

        chunks.forEach(chunk -> uploadChunk(chunk, source, document.embed()));
    }

    private List<DocumentChunk> parseAndChunkDocument(DocumentToUpload document) {
        var builder = new MultipartBodyBuilder();
        builder.part("file", document.content())
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition",
                        "form-data; name=file; filename=" + document.name());

        return Optional.ofNullable(
                pyBackendWebClient.post()
                        .uri("/apy/v1/indexing/parse_pdf")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(builder.build()))
                        .retrieve()
                        .bodyToFlux(DocumentChunk.class)
                        .collectList()
                        .block()
        ).orElse(List.of());
    }

    private DocumentChunk enrichAfterParsing(DocumentToUpload document, DocumentChunk chunk, String userUuid) {
        var source = UPLOAD_SOURCE + document.name();
        return new DocumentChunk(
                chunk.text(),
                Objects.isNull(chunk.url()) ? source : chunk.url(),
                source,
                userUuid,
                document.lang());
    }

    private void uploadChunk(DocumentChunk chunk, SourceEntity source, boolean embed) {
        var documentEntity = new PublicDocumentEntity();
        documentEntity.setSource(source);
        documentEntity.setText(chunk.text());
        documentEntity.setLanguage(chunk.language());
        documentEntity.setUserUuid(chunk.userUuid());
        documentEntity.setUrl(chunk.url());
        if (embed) {
            documentEntity.setTextEmbedding(embeddingModel.embed(chunk.text()));
        }

        documentRepository.save(documentEntity);
    }
}
