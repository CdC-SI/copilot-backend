package zas.admin.zec.backend.actions.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.upload.model.AvailabilityStatus;
import zas.admin.zec.backend.actions.upload.model.DocumentRetentionConfig;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.actions.upload.model.PersonalDoc;
import zas.admin.zec.backend.config.ExceptionsHandler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private final UserService userService = mock(UserService.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final DocumentRetentionConfigService retentionConfigService = mock(DocumentRetentionConfigService.class);
    private final TestingAuthenticationToken principal = new TestingAuthenticationToken("jdoe", null);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(userService.getUuid("jdoe")).thenReturn("user-a");
        mvc = MockMvcBuilders.standaloneSetup(
                        new DocumentController(userService, uploadService, retentionConfigService))
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

    @Test
    void userDocs_listDeleteAndReactivateAreScopedToTheUser() throws Exception {
        when(uploadService.getUserPersonalDocs("user-a")).thenReturn(List.of(
                new PersonalDoc("note.pdf", LocalDateTime.of(2024, 1, 2, 3, 4), EmbeddingStatus.PROCESSED,
                        AvailabilityStatus.ACTIVE, 30L, null)));

        mvc.perform(get("/api/documents/user-docs").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("note.pdf"))
                .andExpect(jsonPath("$[0].timeToLiveInDays").value(30));
        mvc.perform(delete("/api/documents/user-docs").param("filename", "note.pdf").principal(principal))
                .andExpect(status().isOk());
        mvc.perform(put("/api/documents/user-docs/reactivate").param("filename", "note.pdf").principal(principal))
                .andExpect(status().isOk());

        verify(uploadService).deleteUserPersonalDocument("note.pdf", "user-a");
        verify(uploadService).reactivatePersonalDocument("note.pdf", "user-a");
    }

    @Test
    void retentionConfig_getAndUpdate() throws Exception {
        when(retentionConfigService.get()).thenReturn(new DocumentRetentionConfig(30, 60));
        when(retentionConfigService.update(new DocumentRetentionConfig(10, 20))).thenReturn(new DocumentRetentionConfig(10, 20));

        mvc.perform(get("/api/documents/retention-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysBeforeArchival").value(30));
        mvc.perform(put("/api/documents/retention-config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"daysBeforeArchival\":10,\"daysBeforeDeletion\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysBeforeDeletion").value(20));
    }

    @Test
    void retentionConfig_nonPositiveValue_returns400() throws Exception {
        mvc.perform(put("/api/documents/retention-config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"daysBeforeArchival\":0,\"daysBeforeDeletion\":20}"))
                .andExpect(status().isBadRequest());
        verify(retentionConfigService, never()).update(any());
    }

    @Test
    void upload_personalDocumentIsStoredForTheUser() throws Exception {
        mvc.perform(multipart("/api/documents/upload").file(pdf("multipartFile", "note.pdf"))
                        .param("lang", "de").param("embed", "true").principal(principal))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(DocumentToUpload.class);
        verify(uploadService).uploadPersonalDocument(captor.capture(), org.mockito.ArgumentMatchers.eq("user-a"));
        assertThat(captor.getValue().file().getOriginalFilename()).isEqualTo("note.pdf");
        assertThat(captor.getValue().lang()).isEqualTo("de");
        assertThat(captor.getValue().embed()).isTrue();
        assertThat(captor.getValue().faqStore()).isFalse();
    }

    @Test
    void upload_withoutFile_returns400() throws Exception {
        mvc.perform(multipart("/api/documents/upload").param("lang", "fr").principal(principal))
                .andExpect(status().isBadRequest());
        verify(uploadService, never()).uploadPersonalDocument(any(), any());
    }

    @Test
    void uploadAdmin_detectsFaqFilesByName() throws Exception {
        mvc.perform(multipart("/api/documents/upload-admin")
                        .file(pdf("documents", "faq_questions.pdf"))
                        .file(pdf("documents", "guide.pdf"))
                        .principal(principal))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentToUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(uploadService).uploadAdminDocuments(captor.capture());
        assertThat(captor.getValue()).extracting(DocumentToUpload::faqStore).containsExactly(true, false);
        assertThat(captor.getValue()).extracting(DocumentToUpload::lang).containsOnly("fr");
    }

    private static MockMultipartFile pdf(String param, String name) {
        return new MockMultipartFile(param, name, MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII));
    }
}
