package zas.admin.zec.backend.actions.sourceview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.sourceview.SourceView.SourceDisplay;
import zas.admin.zec.backend.actions.sourceview.SourceView.SourceOrigin;
import zas.admin.zec.backend.actions.sourceview.SourceViewService.SourceFile;
import zas.admin.zec.backend.config.ExceptionsHandler;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SourceViewControllerTest {

    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private final UserService userService = mock(UserService.class);
    private final SourceViewService service = mock(SourceViewService.class);
    private final TestingAuthenticationToken principal = new TestingAuthenticationToken("jdoe", null);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(userService.getUuid("jdoe")).thenReturn("user-a");
        mvc = MockMvcBuilders.standaloneSetup(new SourceViewController(userService, service))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void getSource_returnsView() throws Exception {
        when(service.view(ID, "user-a")).thenReturn(new SourceView(ID, SourceOrigin.DOCUMENT, SourceDisplay.PDF,
                "guide.pdf", null, "3", "1.2", null, "in_force", "Extrait", false, true));

        mvc.perform(get("/api/source-documents/{id}", ID).principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(ID))
                .andExpect(jsonPath("$.origin").value("DOCUMENT"))
                .andExpect(jsonPath("$.display").value("PDF"))
                .andExpect(jsonPath("$.title").value("guide.pdf"))
                .andExpect(jsonPath("$.pageNumber").value("3"))
                .andExpect(jsonPath("$.excerpt").value("Extrait"))
                .andExpect(jsonPath("$.fileAvailable").value(true));
    }

    @Test
    void getSource_unknownOrForbidden_returns404() throws Exception {
        when(service.view(ID, "user-a")).thenThrow(new SourceViewNotFoundException("Source not found"));

        mvc.perform(get("/api/source-documents/{id}", ID).principal(principal))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFile_pdf_isServedInlineWithProtectiveHeaders() throws Exception {
        when(service.file(ID, "user-a")).thenReturn(new SourceFile("Règlement.pdf", MediaType.APPLICATION_PDF, new byte[]{1, 2, 3}));

        mvc.perform(get("/api/source-documents/{id}/file", ID).principal(principal))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''R%C3%A8glement.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void getFile_nonPdf_isServedAsAttachment() throws Exception {
        when(service.file(ID, "user-a")).thenReturn(new SourceFile("page.html", MediaType.TEXT_HTML, new byte[]{1}));

        mvc.perform(get("/api/source-documents/{id}/file", ID).principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));
    }

    @Test
    void getFile_withoutFile_returns404() throws Exception {
        when(service.file(ID, "user-a")).thenThrow(new SourceViewNotFoundException("No file"));

        mvc.perform(get("/api/source-documents/{id}/file", ID).principal(principal))
                .andExpect(status().isNotFound());
    }
}
