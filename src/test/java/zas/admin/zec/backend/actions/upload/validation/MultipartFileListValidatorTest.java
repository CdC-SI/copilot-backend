package zas.admin.zec.backend.actions.upload.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MultipartFileListValidatorTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] OLE2_BYTES = ole2Header();

    private final MultipartFileListValidator validator = new MultipartFileListValidator();
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        context = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rapport.docx", "tableau.xlsx", "slides.pptx", "RAPPORT.DOCX"})
    void acceptsOoxmlDocuments(String filename) throws IOException {
        assertTrue(validator.isValid(List.of(file(filename, ooxmlBytes())), context));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ancien.doc", "ancien.xls", "ancien.ppt"})
    void acceptsLegacyOfficeDocuments(String filename) {
        assertTrue(validator.isValid(List.of(file(filename, OLE2_BYTES)), context));
    }

    @Test
    void acceptsPdfAndCsv() {
        assertTrue(validator.isValid(List.of(
                file("source.pdf", PDF_BYTES),
                file("chunks.csv", "a,b\n1,2".getBytes(StandardCharsets.UTF_8))), context));
    }

    @Test
    void rejectsPdfRenamedAsDocx() {
        assertFalse(validator.isValid(List.of(file("fake.docx", PDF_BYTES)), context));
        verify(context).buildConstraintViolationWithTemplate(UploadErrorCode.FILE_CONTENT_INVALID.name());
    }

    @Test
    void rejectsZipRenamedAsDoc() throws IOException {
        assertFalse(validator.isValid(List.of(file("fake.doc", ooxmlBytes())), context));
        verify(context).buildConstraintViolationWithTemplate(UploadErrorCode.FILE_CONTENT_INVALID.name());
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertFalse(validator.isValid(List.of(file("script.exe", PDF_BYTES)), context));
        verify(context).buildConstraintViolationWithTemplate(UploadErrorCode.FILE_TYPE_UNSUPPORTED.name());
    }

    @Test
    void personalDocumentValidatorStillAcceptsOnlyPdf() throws IOException {
        var personalValidator = new MultipartFileValidator();
        assertTrue(personalValidator.isValid(file("source.pdf", PDF_BYTES), context));
        assertFalse(personalValidator.isValid(file("rapport.docx", ooxmlBytes()), context));
        verify(context).buildConstraintViolationWithTemplate(UploadErrorCode.FILE_TYPE_UNSUPPORTED.name());
    }

    private static MockMultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("documents", filename, "application/octet-stream", content);
    }

    private static byte[] ooxmlBytes() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] ole2Header() {
        var bytes = new byte[512];
        byte[] signature = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }
}
