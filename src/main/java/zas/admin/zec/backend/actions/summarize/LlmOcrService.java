package zas.admin.zec.backend.actions.summarize;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.Tika;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class LlmOcrService {

    private static final List<String> IMAGE_MIME_TYPES = List.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/gif",
            "image/bmp",
            "image/webp",
            "image/tiff"
    );

    private final ChatModel visionModel;
    private final Tika tika = new Tika();

    public LlmOcrService(@Qualifier("visionModel") ChatModel visionModel) {
        this.visionModel = visionModel;
    }

    public String ocrPdf(byte[] fileBytes) throws IOException {
        String mimeType = detectMimeType(fileBytes);
        log.info("Detected MIME type: {}", mimeType);

        if ("application/pdf".equals(mimeType)) {
            return ocrPdfDocument(fileBytes);
        } else if (IMAGE_MIME_TYPES.contains(mimeType)) {
            return ocrImage(fileBytes);
        } else {
            throw new IllegalArgumentException("Type de fichier non supporté: " + mimeType);
        }
    }

    private String detectMimeType(byte[] fileBytes) throws IOException {
        try (var bis = new ByteArrayInputStream(fileBytes)) {
            return tika.detect(bis);
        }
    }

    private String ocrPdfDocument(byte[] pdfBytes) throws IOException {
        int dpi = 150;

        String ocrPrompt = """
                Tu es un OCR. Extrais fidèlement le texte de l'image.
                Respecte la mise en page (paragraphes, listes).
                Ne rajoute rien, ne résume pas.
                """;

        var sb = new StringBuilder(16_384);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) { // PDFBox 3.x
            var renderer = new PDFRenderer(doc);
            int pages = doc.getNumberOfPages();

            for (int i = 0; i < pages; i++) {
                byte[] pngBytes = renderPagePng(renderer, i, dpi);
                String pageText = performOcr(pngBytes, ocrPrompt);

                sb.append("\n\n=== PAGE ").append(i + 1).append(" / ").append(pages).append(" ===\n\n")
                        .append(pageText == null ? "" : pageText);
            }
        }

        return sb.toString().trim();
    }

    private String ocrImage(byte[] imageBytes) throws IOException {
        log.debug("Processing single image for OCR");

        String ocrPrompt = """
                Tu es un OCR. Extrais fidèlement le texte de l'image.
                Respecte la mise en page (paragraphes, listes).
                Ne rajoute rien, ne résume pas.
                """;

        byte[] pngBytes = convertToPng(imageBytes);
        return performOcr(pngBytes, ocrPrompt);
    }

    private byte[] convertToPng(byte[] imageBytes) throws IOException {
        try (var inputStream = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) {
                throw new IOException("Impossible de lire l'image");
            }

            try (var baos = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", baos);
                return baos.toByteArray();
            }
        }
    }

    private String performOcr(byte[] pngBytes, String prompt) {
        var userMessage = UserMessage.builder()
                .text(prompt)
                .media(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(pngBytes)))
                .build();

        var response = visionModel.call(new Prompt(userMessage));
        return response.getResult().getOutput().getText();
    }

    private static byte[] renderPagePng(PDFRenderer renderer, int pageIndex, int dpi) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
        try (var baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }
}

