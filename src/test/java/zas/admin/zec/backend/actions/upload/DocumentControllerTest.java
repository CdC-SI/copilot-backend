package zas.admin.zec.backend.actions.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.ExceptionsHandler;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private final UserService userService = mock(UserService.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final TestingAuthenticationToken principal = new TestingAuthenticationToken("jdoe", null);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(userService.getUuid("jdoe")).thenReturn("user-a");
        mvc = MockMvcBuilders.standaloneSetup(
                        new DocumentController(userService, uploadService, mock(DocumentRetentionConfigService.class)))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    void getDocument_servesTheDocumentResolvedForTheCurrentUser() throws Exception {
        when(uploadService.download("guide.pdf", "user-a")).thenReturn(
                new UploadService.Doc("guide.pdf", MediaType.APPLICATION_PDF, new ByteArrayResource(new byte[]{1})));

        mvc.perform(get("/api/documents").param("filename", "guide.pdf").principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void getDocument_returns404WhenNoAccessibleDocument() throws Exception {
        when(uploadService.download("autre.pdf", "user-a")).thenThrow(new DocumentNotFoundException("autre.pdf"));

        mvc.perform(get("/api/documents").param("filename", "autre.pdf").principal(principal))
                .andExpect(status().isNotFound());
    }
}
