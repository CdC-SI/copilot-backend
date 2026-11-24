package zas.admin.zec.backend.actions.summarize;

import ch.admin.zas.gaime.dao.domain.doc.DocumentSearchVO;
import ch.admin.zas.gaime.dao.domain.doc.DocumentService;
import ch.admin.zas.gaime.dao.domain.doc.DocumentVO;
import io.micrometer.observation.ObservationRegistry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.jms.core.JmsTemplate;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.actions.summarize.jms.GaimeJmsService;
import zas.admin.zec.backend.config.ChatClientConfig;
import zas.admin.zec.backend.persistence.entity.SummaryTaskEntity;
import zas.admin.zec.backend.persistence.repository.SummaryTaskRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chaîne de synthèse des certificats d'études : OCR (LLM vision simulé), synthèse (LLM simulé),
 * traitement asynchrone d'une tâche et envoi JMS vers GAIME.
 */
class SummarizePipelineTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(i -> {
            prompts.add(i.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("texte " + prompts.size()))));
        });
    }

    private LlmOcrService ocr() {
        return new LlmOcrService(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP));
    }

    @Test
    void ocr_pdf_concatenatesPagesWithHeaders() throws IOException {
        var text = ocr().ocrFile(pdf(2));

        assertThat(text).isEqualTo("=== PAGE 1 / 2 ===\n\ntexte 1\n\n=== PAGE 2 / 2 ===\n\ntexte 2");
        assertThat(prompts).hasSize(2);
        assertThat(prompts.getFirst().getUserMessage().getMedia()).hasSize(1);
        assertThat(prompts.getFirst().getUserMessage().getText()).contains("Tu es un OCR");
    }

    @Test
    void ocr_image_singleCall() throws IOException {
        assertThat(ocr().ocrFile(png())).isEqualTo("texte 1");
    }

    @Test
    void ocr_detectsTypeFromContentNotFromName() throws IOException {
        var service = ocr();

        assertThat(service.isPdf(pdf(1))).isTrue();
        assertThat(service.isPdf(png())).isFalse();
        assertThat(service.isSupported(png())).isTrue();
        assertThat(service.isSupported("bonjour".getBytes())).isFalse();
        assertThatThrownBy(() -> service.ocrFile("bonjour".getBytes()))
                .isInstanceOf(LlmOcrService.UnsupportedFileTypeException.class)
                .hasMessageContaining("text/plain");
    }

    @Test
    void synthesis_wrapsEachCertificate() {
        var service = new LlmStudySynthesisService(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP));

        assertThat(service.summarizeStudies(List.of("Certificat A", "Certificat B"))).isEqualTo("texte 1");
        assertThat(prompts.getFirst().getUserMessage().getText())
                .contains("<STUDY_CERTIFICATE>\nCertificat A\n</STUDY_CERTIFICATE>")
                .contains("<STUDY_CERTIFICATE>\nCertificat B\n</STUDY_CERTIFICATE>");
    }

    @Test
    void asyncProcessor_success_storesSummaryAndReferences() throws IOException {
        var ocr = mock(LlmOcrService.class);
        var synthesis = mock(LlmStudySynthesisService.class);
        var documents = mock(DocumentService.class);
        var repository = mock(SummaryTaskRepository.class);
        var task = task();
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        var doc1 = document("tok1");
        var doc2 = document("tok2");
        when(documents.getDocuments(any(DocumentSearchVO.class), eq(DocumentVO.class))).thenReturn(List.of(doc1, doc2));
        when(documents.getDocumentContent(doc1)).thenReturn(new byte[]{1});
        when(documents.getDocumentContent(doc2)).thenReturn(new byte[]{2});
        when(ocr.ocrFile(new byte[]{1})).thenReturn("certificat 1");
        when(ocr.ocrFile(new byte[]{2})).thenThrow(new IOException("illisible"));
        when(synthesis.summarizeStudies(anyList())).thenReturn("# Synthèse");

        new SummarizeAsyncProcessor(ocr, synthesis, documents, repository).processSummarization(1L);

        // Un document illisible ne bloque pas la synthèse : un texte de remplacement est transmis.
        verify(synthesis).summarizeStudies(List.of("certificat 1", "Pas de contenu disponible pour ce document : tok2"));
        assertThat(task.getStatus()).isEqualTo(SummaryTaskStatus.TERMINEE);
        assertThat(task.getSummaryMarkdown()).isEqualTo("# Synthèse");
        assertThat(task.getReferences()).containsExactly("tok1", "tok2");
        verify(repository).save(task);
    }

    @Test
    void asyncProcessor_noDocuments_marksTaskInError() {
        var synthesis = mock(LlmStudySynthesisService.class);
        var documents = mock(DocumentService.class);
        var repository = mock(SummaryTaskRepository.class);
        var task = task();
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        when(documents.getDocuments(any(DocumentSearchVO.class), eq(DocumentVO.class))).thenReturn(List.of());

        new SummarizeAsyncProcessor(mock(LlmOcrService.class), synthesis, documents, repository).processSummarization(1L);

        assertThat(task.getStatus()).isEqualTo(SummaryTaskStatus.ERREUR);
        assertThat(task.getErrorMessage()).isEqualTo("Aucun document trouvé pour ce numéro AVS");
        verify(synthesis, never()).summarizeStudies(anyList());
    }

    @Test
    void asyncProcessor_unexpectedFailure_marksTaskInError() {
        var synthesis = mock(LlmStudySynthesisService.class);
        var documents = mock(DocumentService.class);
        var repository = mock(SummaryTaskRepository.class);
        var task = task();
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        when(documents.getDocuments(any(DocumentSearchVO.class), eq(DocumentVO.class))).thenThrow(new IllegalStateException("GAIME indisponible"));

        new SummarizeAsyncProcessor(mock(LlmOcrService.class), synthesis, documents, repository).processSummarization(1L);

        assertThat(task.getStatus()).isEqualTo(SummaryTaskStatus.ERREUR);
        assertThat(task.getErrorMessage()).isEqualTo("Erreur: GAIME indisponible");
        verify(repository).save(task);
    }

    @Test
    void asyncProcessor_unknownTask_throws() {
        var repository = mock(SummaryTaskRepository.class);
        when(repository.findById(9L)).thenReturn(Optional.empty());
        var processor = new SummarizeAsyncProcessor(mock(LlmOcrService.class), mock(LlmStudySynthesisService.class),
                mock(DocumentService.class), repository);

        assertThatThrownBy(() -> processor.processSummarization(9L)).hasMessageContaining("9");
    }

    @Test
    void gaime_sendsPrefixedJsonMessage() {
        var jms = mock(JmsTemplate.class);

        new GaimeJmsService(jms, JsonMapper.builder().build()).sendOpenDocumentsMessage("JDU", List.of("t1", "t2"));

        verify(jms).convertAndSend("GAIME={\"action\":\"viewDocuments\",\"visa\":\"JDU\",\"objTokens\":[\"t1\",\"t2\"]}");
    }

    private static SummaryTaskEntity task() {
        var task = new SummaryTaskEntity();
        task.setId(1L);
        task.setNavs("7561234567897");
        task.setStatus(SummaryTaskStatus.EN_COURS);
        return task;
    }

    private static DocumentVO document(String token) {
        var doc = mock(DocumentVO.class);
        when(doc.getObjectToken()).thenReturn(token);
        return doc;
    }

    private static byte[] png() throws IOException {
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        }
    }

    private static byte[] pdf(int pages) throws IOException {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
