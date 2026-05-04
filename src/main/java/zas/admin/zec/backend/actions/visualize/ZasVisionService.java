package zas.admin.zec.backend.actions.visualize;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import zas.admin.zec.backend.actions.visualize.model.MedicalServices;
import zas.admin.zec.backend.actions.visualize.model.TextTranslation;
import zas.admin.zec.backend.actions.visualize.model.ZasDocumentType;
import zas.admin.zec.backend.actions.visualize.model.sumex.SumexInvoice;
import zas.admin.zec.backend.tools.JsonSchemaBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ZasVisionService implements VisionService {
    private final ChatClient visionChatClient;
    private final ChatClient llmChatClient;
    private final VisionMessageService visionMessageService;

    private static final List<String> IMAGE_MIME_TYPES = List.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/bmp", "image/webp", "image/tiff"
    );

    public ZasVisionService(@Qualifier("visionModel") ChatModel visionModel,
                            @Qualifier("internalChatModel") ChatModel llmModel,
                            VisionMessageService visionMessageService) {

        this.visionChatClient = ChatClient.create(visionModel);
        this.llmChatClient = ChatClient.create(llmModel);
        this.visionMessageService = visionMessageService;
    }

    public JsonNode extractFieldsFromFile(MultipartFile file, List<String> fields) {
        String jsonSchema = JsonSchemaBuilder.buildFlatJsonSchema(fields);

        var options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                .build();

        var prompt = new Prompt(
                visionMessageService.structureDataFromImageMessage(jsonSchema),
                visionMessageService.fileMessage(file));

        return visionChatClient.prompt(prompt).options(options).call().entity(JsonNode.class);
    }

    public MedicalServices extractTariffPositionsFromFile(MultipartFile file) {
        String jsonSchema = JsonSchemaGenerator.generateForType(MedicalServices.class);

        var options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                .build();

        var prompt = new Prompt(
                visionMessageService.extractTariffPositionMessage(jsonSchema),
                visionMessageService.fileMessage(file));

        return visionChatClient.prompt(prompt).options(options).call().entity(MedicalServices.class);
    }

    public SumexInvoice extractSumexInvoiceFromFile(MultipartFile file) {
        String jsonSchema = JsonSchemaGenerator.generateForType(SumexInvoice.class);

        var options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                .build();

        var prompt = new Prompt(
                visionMessageService.extractSumexInvoiceMessage(jsonSchema),
                visionMessageService.fileMessage(file));

        return visionChatClient.prompt(prompt).options(options).call().entity(SumexInvoice.class);
    }

    @Override
    public ZasDocumentType classifyFile(MultipartFile file) {
        String jsonSchema = JsonSchemaGenerator.generateForType(ZasDocumentType.class);
        var options = OpenAiChatOptions.builder()
                .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                .build();

        var prompt = new Prompt(visionMessageService.classifyMessage(), visionMessageService.fileMessage(file));

        return visionChatClient.prompt(prompt).options(options).call().entity(ZasDocumentType.class);
    }

    @Override
    public List<TextTranslation> translateFile(MultipartFile file, String language) {
        try {
            String contentType = file.getContentType();
            byte[] fileBytes = file.getBytes();

            List<byte[]> pages;
            if ("application/pdf".equals(contentType)) {
                pages = pdfToImages(fileBytes);
            } else if (contentType != null && IMAGE_MIME_TYPES.contains(contentType)) {
                pages = List.of(convertToPng(fileBytes));
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + contentType);
            }

            var systemMessage = visionMessageService.translateImageMessage(language);
            String jsonSchema = JsonSchemaGenerator.generateForType(TextTranslation.class);
            var options = OpenAiChatOptions.builder()
                    .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema))
                    .build();
            List<CompletableFuture<TextTranslation>> futures = pages.stream().map(pageBytes -> CompletableFuture.supplyAsync(() -> {
                var userMessage = UserMessage.builder()
                            .text("")
                            .media(List.of(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new ByteArrayResource(pageBytes)).build()))
                            .build();
                var prompt = new Prompt(systemMessage, userMessage);
                return visionChatClient.prompt(prompt).options(options).call().entity(TextTranslation.class);
            })).toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

        } catch (IOException e) {
            throw new RuntimeException("Failed to process file for translation", e);
        }
    }

    private List<byte[]> pdfToImages(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            var renderer = new PDFRenderer(doc);
            int pages = doc.getNumberOfPages();
            List<byte[]> images = new ArrayList<>(pages);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                try (var baos = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", baos);
                    images.add(baos.toByteArray());
                }
            }
            return images;
        }
    }

    private byte[] convertToPng(byte[] imageBytes) throws IOException {
        var image = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (image == null) throw new IOException("Cannot read image");
        try (var baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }
}
