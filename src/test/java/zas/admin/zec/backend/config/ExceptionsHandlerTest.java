package zas.admin.zec.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;
import zas.admin.zec.backend.actions.upload.validation.UploadException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionsHandlerTest {

    private final ExceptionsHandler handler = new ExceptionsHandler();

    @Test
    @DisplayName("returns the upload error code, file name and matching HTTP status")
    void handleUploadException_returnsErrorCode() {
        var request = new MockHttpServletRequest("POST", "/api/conversations/attachments");
        var exception = new UploadException("doc.docx", UploadErrorCode.FILE_TYPE_UNSUPPORTED, "unsupported");

        var response = handler.handleUploadException(request, exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("STATUS"));
        assertEquals("FILE_TYPE_UNSUPPORTED", body.get("MESSAGE"));
        assertEquals("/api/conversations/attachments", body.get("PATH"));
        assertEquals("doc.docx", body.get("FILENAME"));
    }

    @Test
    @DisplayName("maps server-side upload errors to a 5xx status without file name when unknown")
    void handleUploadException_serverError() {
        var request = new MockHttpServletRequest("POST", "/api/documents/upload");
        var exception = new UploadException(null, UploadErrorCode.UPLOAD_FAILED, "db down", new IOException());

        var response = handler.handleUploadException(request, exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("UPLOAD_FAILED", response.getBody().get("MESSAGE"));
        assertFalse(response.getBody().containsKey("FILENAME"));
    }
}
