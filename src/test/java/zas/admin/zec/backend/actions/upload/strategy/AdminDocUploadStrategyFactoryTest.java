package zas.admin.zec.backend.actions.upload.strategy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.QuestionRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class AdminDocUploadStrategyFactoryTest {

    private final AdminDocUploadStrategyFactory factory = new AdminDocUploadStrategyFactory(
            mock(TempSourceDocumentRepository.class), mock(DocumentRepository.class), mock(QuestionRepository.class));

    @Test
    void csvSentAsExcelMimeTypeUsesEmbeddedStrategy() {
        var doc = new DocumentToUpload(new MockMultipartFile("documents", "chunks.csv", "application/vnd.ms-excel", new byte[0]));
        assertInstanceOf(EmbeddedDocUploadStrategy.class, factory.getUploadStrategy(doc));
    }

    @Test
    void excelUsesSourceStrategy() {
        var doc = new DocumentToUpload(new MockMultipartFile("documents", "tableau.xlsx", "application/vnd.ms-excel", new byte[0]));
        assertInstanceOf(SourceDocUploadStrategy.class, factory.getUploadStrategy(doc));
    }

    @Test
    void wordUsesSourceStrategy() {
        var doc = new DocumentToUpload(new MockMultipartFile("documents", "rapport.docx", "application/msword", new byte[0]));
        assertInstanceOf(SourceDocUploadStrategy.class, factory.getUploadStrategy(doc));
    }
}
