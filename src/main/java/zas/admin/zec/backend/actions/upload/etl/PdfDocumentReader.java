package zas.admin.zec.backend.actions.upload.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class PdfDocumentReader implements DocumentReader {

    private final MultipartFile file;

    public PdfDocumentReader(MultipartFile file) {
        this.file = file;
    }

    @Override
    public List<Document> get() {
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(file.getResource(),
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build());

        return pdfReader.read();
    }
}
