package zas.admin.zec.backend.actions.upload;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;
import zas.admin.zec.backend.actions.upload.etl.PdfDocumentReader;
import zas.admin.zec.backend.actions.upload.model.DocumentRetentionConfig;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.actions.upload.strategy.EmbeddedDocUploadStrategy;
import zas.admin.zec.backend.actions.upload.strategy.SourceDocUploadStrategy;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.entity.DocumentRetentionConfigEntity;
import zas.admin.zec.backend.persistence.entity.QuestionEntity;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.DocumentRetentionConfigRepository;
import zas.admin.zec.backend.persistence.repository.QuestionRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stratégies d'upload administrateur (CSV pré-embeddés, documents sources), configuration de rétention,
 * poller des jobs d'embedding et lecture PDF page par page.
 */
class UploadComponentsTest {

    private static final String CSV_HEADER = "content,metadata,embedding\n";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final EmbeddedDocUploadStrategy embedded = new EmbeddedDocUploadStrategy(documentRepository, questionRepository);

    @Test
    void embeddedCsv_documentsAreParsedAndSaved() {
        var saved = captureSaved(documentRepository);
        var csv = CSV_HEADER + "\"Texte A\",\"{'source':'rh','page':1}\",\"0.1, 0.2,0.3\"\n";

        embedded.upload(new DocumentToUpload(csvFile("docs.csv", csv)));

        verify(documentRepository).flush();
        var entity = (DocumentEntity) saved.getFirst();
        assertThat(entity.getContent()).isEqualTo("Texte A");
        assertThat(entity.getMetadata()).containsEntry("source", "rh").containsEntry("page", 1);
        assertThat(entity.getEmbedding()).containsExactly(0.1f, 0.2f, 0.3f);
        verify(questionRepository, never()).saveAll(anyList());
    }

    @Test
    void embeddedCsv_faqFilesGoToQuestionStore() {
        var saved = captureSaved(questionRepository);
        var csv = CSV_HEADER + "\"Question ?\",\"{\"\"answer_id\"\":\"\"a1\"\"}\",\"1,2\"\n";

        embedded.upload(new DocumentToUpload(csvFile("faq_questions.csv", csv)));

        assertThat(((QuestionEntity) saved.getFirst()).getMetadata()).containsEntry("answer_id", "a1");
        verify(documentRepository, never()).saveAll(anyList());
    }

    @Test
    void embeddedCsv_largeFilesAreFlushedByBatchesOfThousand() {
        var csv = new StringBuilder(CSV_HEADER);
        for (int i = 0; i < 1001; i++) {
            csv.append("\"t").append(i).append("\",\"{}\",\"1\"\n");
        }

        embedded.upload(new DocumentToUpload(csvFile("docs.csv", csv.toString())));

        verify(documentRepository, org.mockito.Mockito.times(2)).saveAll(anyList());
        verify(documentRepository, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void embeddedCsv_invalidContent_isReportedAsCsvContentInvalid() {
        var badJson = CSV_HEADER + "\"Texte\",\"{pas du json\",\"1\"\n";
        var badEmbedding = CSV_HEADER + "\"Texte\",\"{}\",\"abc\"\n";

        for (var csv : List.of(badJson, badEmbedding)) {
            assertThatThrownBy(() -> embedded.upload(new DocumentToUpload(csvFile("docs.csv", csv))))
                    .isInstanceOfSatisfying(UploadException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(UploadErrorCode.CSV_CONTENT_INVALID);
                        assertThat(e.getFileName()).isEqualTo("docs.csv");
                    });
        }
        verify(documentRepository, never()).saveAll(anyList());
    }

    @Test
    void embeddedCsv_unreadableFile_isReportedAsReadError() throws IOException {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("docs.csv");
        when(file.getBytes()).thenThrow(new IOException("disque"));

        assertThatThrownBy(() -> embedded.upload(new DocumentToUpload(file, "fr", false, false)))
                .isInstanceOfSatisfying(UploadException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UploadErrorCode.FILE_READ_ERROR));
    }

    @Test
    void sourceDoc_isStoredAsProcessedTemporaryDocument() throws IOException {
        var repository = mock(TempSourceDocumentRepository.class);

        new SourceDocUploadStrategy(repository).upload(new DocumentToUpload(csvFile("guide.pdf", "contenu")));

        var captor = ArgumentCaptor.forClass(TempSourceDocumentEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFileName()).isEqualTo("guide.pdf");
        assertThat(captor.getValue().getContent()).isEqualTo("contenu".getBytes(StandardCharsets.UTF_8));
        assertThat(captor.getValue().getStatus()).isEqualTo(EmbeddingStatus.PROCESSED);
        assertThat(captor.getValue().getUploadedAt()).isNotNull();

        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("x.pdf");
        when(file.getBytes()).thenThrow(new IOException("disque"));
        assertThatThrownBy(() -> new SourceDocUploadStrategy(repository).upload(new DocumentToUpload(file, "fr", false, false)))
                .isInstanceOfSatisfying(UploadException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UploadErrorCode.FILE_READ_ERROR));
    }

    @Test
    void retentionConfig_readsAndUpdatesTheSingleRow() {
        var repository = mock(DocumentRetentionConfigRepository.class);
        var entity = new DocumentRetentionConfigEntity();
        entity.setDaysBeforeArchival(30);
        entity.setDaysBeforeDeletion(90);
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(entity));
        var service = new DocumentRetentionConfigService(repository);

        assertThat(service.get()).isEqualTo(new DocumentRetentionConfig(30, 90));
        assertThat(service.update(new DocumentRetentionConfig(7, 14))).isEqualTo(new DocumentRetentionConfig(7, 14));
        assertThat(entity.getDaysBeforeArchival()).isEqualTo(7);
        verify(repository).save(entity);
    }

    @Test
    void retentionConfig_missingRow_failsExplicitly() {
        var repository = mock(DocumentRetentionConfigRepository.class);
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DocumentRetentionConfigService(repository).get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rétention");
    }

    @Test
    void poller_dispatchesClaimedDocumentsAndReleasesRejectedOnes() {
        var processor = mock(EmbeddingJobProcessor.class);
        var executor = mock(TaskExecutor.class);
        when(processor.claimDue()).thenReturn(List.of(1L, 2L));
        doAnswer(i -> {
            ((Runnable) i.getArgument(0)).run();
            return null;
        }).doThrow(new TaskRejectedException("plein")).when(executor).execute(any(Runnable.class));

        new EmbeddingJobPoller(processor, executor).pollPendingDocuments();

        verify(processor).process(1L);
        verify(processor, never()).process(2L);
        verify(processor).release(2L);
    }

    @Test
    void poller_claimFailure_isSwallowed() {
        var processor = mock(EmbeddingJobProcessor.class);
        var executor = mock(TaskExecutor.class);
        doThrow(new IllegalStateException("base indisponible")).when(processor).claimDue();

        new EmbeddingJobPoller(processor, executor).pollPendingDocuments();

        verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void pdfReader_returnsOneDocumentPerPage() throws IOException {
        var reader = new PdfDocumentReader(new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf("Bonjour", "Merci")));

        var documents = reader.get();

        assertThat(documents).hasSize(2);
        assertThat(documents.getFirst().getText()).contains("Bonjour");
        assertThat(documents.get(1).getText()).contains("Merci");
    }

    /** Le lot est vidé après saveAll : on en copie le contenu au moment de l'appel. */
    private static List<Object> captureSaved(org.springframework.data.jpa.repository.JpaRepository<?, ?> repository) {
        var saved = new java.util.ArrayList<Object>();
        doAnswer(i -> {
            saved.addAll(i.getArgument(0));
            return List.of();
        }).when(repository).saveAll(anyList());
        return saved;
    }

    private static MockMultipartFile csvFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] pdf(String... pageTexts) throws IOException {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (var text : pageTexts) {
                var page = new PDPage();
                doc.addPage(page);
                try (var stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
