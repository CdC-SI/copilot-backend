package zas.admin.zec.backend.actions.visualize;

import io.micrometer.observation.ObservationRegistry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.visualize.model.ZasDocumentType;
import zas.admin.zec.backend.config.ChatClientConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service de vision avec un modèle de chat simulé : vérifie les prompts envoyés (messages réels de
 * {@link ZasMessageService}) et la désérialisation des réponses structurées.
 */
class ZasVisionServiceTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private ZasVisionService service;

    @BeforeEach
    void setUp() {
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        service = new ZasVisionService(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), new ZasMessageService());
    }

    private void answer(String json) {
        when(chatModel.call(any(Prompt.class))).thenAnswer(i -> {
            prompts.add(i.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        });
    }

    @Test
    void extractFields_sendsSchemaAndFileAndReturnsJson() {
        answer("{\"name\":\"Dupont\",\"navs\":\"756.1234.5678.97\"}");

        var result = service.extractFieldsFromFile(png("id.png"), List.of("name", "navs"));

        assertThat(result.get("name").asString()).isEqualTo("Dupont");
        var user = userMessages(prompts.getFirst());
        assertThat(user.getFirst().getText()).contains("FORMAT_SORTIE").contains("navs");
        assertThat(user.get(1).getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType().toString()).isEqualTo("image/png"));
    }

    @Test
    void classify_returnsDocumentTypeAndListsAllTypesInPrompt() {
        answer("{\"type\":\"PASSPORT\"}");

        assertThat(service.classifyFile(png("p.png")).type()).isEqualTo(ZasDocumentType.Type.PASSPORT);
        assertThat(userMessages(prompts.getFirst()).getFirst().getText()).contains("ID_CARD, PASSPORT", "UNKNOWN");
    }

    @Test
    void extractTariffPositions_parsesMedicalServices() {
        answer("{\"medicalServices\":[{\"date\":\"2024-01-02\",\"tariffNumber\":\"001\",\"tariffCode\":\"4.8610\",\"serviceDescription\":\"Consultation\",\"confidence\":\"high\"}]}");

        var services = service.extractTariffPositionsFromFile(png("f.png"));

        assertThat(services.medicalServices()).singleElement()
                .satisfies(s -> assertThat(s.tariffCode()).isEqualTo("4.8610"));
        assertThat(systemText(prompts.getFirst())).contains("Chiffre Tarif");
    }

    @Test
    void extractSumexInvoice_parsesInvoice() {
        answer("{}");

        var invoice = service.extractSumexInvoiceFromFile(png("f.png"));

        assertThat(invoice).isNotNull();
        assertThat(systemText(prompts.getFirst())).contains("InvoiceAuthor");
    }

    @Test
    void translate_image_translatesSinglePage() {
        answer("{\"translatedText\":\"Bonjour\",\"detectedLanguage\":\"Allemand\",\"targetLanguage\":\"fr\"}");

        var result = service.translateFile(png("p.png"), "fr");

        assertThat(result).singleElement().satisfies(t -> assertThat(t.translatedText()).isEqualTo("Bonjour"));
        var prompt = prompts.getFirst();
        assertThat(systemText(prompt)).contains("Translate ALL extracted text into fr");
        assertThat(userMessages(prompt).getFirst().getText()).contains("Target language: fr");
        assertThat(userMessages(prompt).getFirst().getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType().toString()).isEqualTo("image/png"));
    }

    @Test
    void translate_pdf_translatesEachPageInOrder() throws IOException {
        answer("{\"translatedText\":\"page\",\"detectedLanguage\":\"fr\",\"targetLanguage\":\"de\"}");

        var result = service.translateFile(pdf(2), "de");

        assertThat(result).hasSize(2);
        assertThat(prompts).hasSize(2);
    }

    @Test
    void translate_unsupportedType_isRejectedWithoutLlmCall() {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.translateFile(file, "fr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text/plain");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void translate_unreadableImage_failsWithRuntimeException() {
        var file = new MockMultipartFile("file", "a.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> service.translateFile(file, "fr"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to process file for translation");
    }

    @Test
    void messageService_otherMessages() {
        var messages = new ZasMessageService();

        assertThat(messages.dataInstructionsMessage().getText()).contains("756.XXXX.XXXX.XX");
        assertThat(messages.extractTextMessage().getText()).contains("extract text");
        assertThat(messages.translateMessage("it", "Guten Tag").getText()).contains("it").contains("Guten Tag");
    }

    private static List<UserMessage> userMessages(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(UserMessage.class::cast)
                .toList();
    }

    private static String systemText(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .findFirst().orElseThrow().getText();
    }

    static MultipartFile png(String name) {
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", out);
            return new MockMultipartFile("file", name, "image/png", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static MultipartFile pdf(int pages) throws IOException {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return new MockMultipartFile("file", "doc.pdf", "application/pdf", out.toByteArray());
        }
    }
}
