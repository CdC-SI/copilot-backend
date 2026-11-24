package zas.admin.zec.backend.actions.converse;

import ch.admin.zas.jweb.securityevents.core.utils.OPDOOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.authorize.User;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.upload.UploadService;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.config.ExceptionsHandler;
import zas.admin.zec.backend.tools.OpdoPersonalData;
import zas.admin.zec.backend.tools.SecurityLogging;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints de consultation et de gestion des conversations, flux SSE et pièces jointes. */
class ConversationControllerTest {

    private static final String USER = "user";

    private final UserService userService = mock(UserService.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final SecurityLogging securityLogging = mock(SecurityLogging.class);
    private final TestingAuthenticationToken authentication = new TestingAuthenticationToken("jdoe", null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(userService.getUuid("jdoe")).thenReturn(USER);
        when(userService.getByUsername("jdoe"))
                .thenReturn(new User("jdoe", "John", "Doe", null, List.of(), List.of(), true));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConversationController(userService, conversationService, securityLogging))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    @DisplayName("liste des workspaces")
    void workspaces() throws Exception {
        when(conversationService.getWorkspaces()).thenReturn(List.of("AVS", "RH"));

        mockMvc.perform(get("/api/conversations/workspaces").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("AVS"))
                .andExpect(jsonPath("$[1]").value("RH"));
    }

    @Test
    @DisplayName("titres de l'utilisateur, accès journalisé (OPDO lecture)")
    void titles_areLoggedAsPersonalDataAccess() throws Exception {
        when(conversationService.getTitlesByUserId(USER)).thenReturn(List.of(
                new ConversationTitle("Rente AVS", USER, "conv", ConversationType.COMPLETE, LocalDateTime.of(2026, 1, 1, 10, 0))));

        mockMvc.perform(get("/api/conversations/titles").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Rente AVS"))
                .andExpect(jsonPath("$[0].conversationId").value("conv"));

        verify(securityLogging).log(eq("Accès aux conversations de l'utilisateur"), eq(OPDOOperation.READING),
                ArgumentMatchers.<List<OpdoPersonalData>>argThat(data -> data.size() == 1
                        && "firstName=John, lastName=Doe".equals(data.getFirst().print())));
    }

    @Test
    @DisplayName("renommage d'une conversation (newTitle ou new_title)")
    void rename() throws Exception {
        mockMvc.perform(put("/api/conversations/titles/conv").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"new_title\":\"Nouveau titre\"}"))
                .andExpect(status().isOk());

        verify(conversationService).renameConversation(USER, "conv", "Nouveau titre");
    }

    @Test
    @DisplayName("contenu d'une conversation de l'utilisateur, accès journalisé")
    void conversation() throws Exception {
        when(conversationService.getByConversationIdAndUserId("conv", USER))
                .thenReturn(new Conversation("conv", USER, List.of(), List.of()));

        mockMvc.perform(get("/api/conversations/conv").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv"));

        verify(securityLogging).log(eq("Accès au contenu d'une conversation"), eq(OPDOOperation.READING), anyList());
    }

    @Test
    @DisplayName("initialisation d'une conversation FAQ : 201 et identifiant de conversation généré")
    void init() throws Exception {
        mockMvc.perform(post("/api/conversations/init").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"lang\":\"fr\",\"message\":\"Q ?\",\"source\":\"USER\"},"
                                + "{\"lang\":\"fr\",\"message\":\"R.\",\"source\":\"FAQ\"}]"))
                .andExpect(status().isCreated());

        verify(conversationService).initConversation(eq(USER), argThat(id -> id != null && !id.isBlank()),
                argThat(messages -> messages.size() == 2 && messages.getFirst().message().equals("Q ?")));
    }

    @Test
    @DisplayName("ajout de messages FAQ à une conversation existante")
    void update() throws Exception {
        mockMvc.perform(put("/api/conversations/conv").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"lang\":\"fr\",\"message\":\"Q ?\",\"source\":\"USER\"}]"))
                .andExpect(status().isOk());

        verify(conversationService).update(eq(USER), eq("conv"), argThat(messages -> messages.size() == 1));
    }

    @Test
    @DisplayName("suppression d'une conversation, journalisée (OPDO suppression)")
    void deleteConversation() throws Exception {
        mockMvc.perform(delete("/api/conversations/conv").principal(authentication))
                .andExpect(status().isOk());

        verify(conversationService).delete(USER, "conv");
        verify(securityLogging).log(eq("Suppression d'une conversation"), eq(OPDOOperation.DELETION), anyList());
    }

    @Test
    @DisplayName("question : flux SSE typé (event + data JSON)")
    void askQuestion_streamsTypedServerSentEvents() throws Exception {
        when(conversationService.streamAnswer(any(), eq(USER))).thenReturn(Flux.just(
                ChatStreamEvent.start("conv", "m1"), ChatStreamEvent.textDelta("Bonjour"),
                ChatStreamEvent.done("m1", false)));

        var result = mockMvc.perform(multipart("/api/conversations")
                        .param("query", "Question ?")
                        .param("conversationId", "conv")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .principal(authentication))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("event:start"), body);
        assertTrue(body.contains("\"conversationId\":\"conv\""), body);
        assertTrue(body.contains("event:text_delta"), body);
        assertTrue(body.contains("event:done"), body);
        // Valeurs par défaut appliquées avant l'appel du service.
        verify(conversationService).streamAnswer(argThat(question -> "fr".equals(question.language())
                && "conv".equals(question.conversationId()) && question.replaceMessageId() == null), eq(USER));
    }

    @Test
    @DisplayName("téléchargement d'une pièce jointe : octets et nom de fichier encodé")
    void attachmentDownload() throws Exception {
        when(conversationService.getAttachment("conv", 7L, USER)).thenReturn(new UploadService.Doc("relevé é.pdf",
                MediaType.APPLICATION_OCTET_STREAM, new ByteArrayResource("PDF".getBytes())));

        mockMvc.perform(get("/api/conversations/conv/attachments/7").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''relev%C3%A9%20%C3%A9.pdf")))
                .andExpect(content().bytes("PDF".getBytes()));
    }

    @Test
    @DisplayName("upload de pièces jointes : 202 PENDING ; conversation optionnelle")
    void attachmentUpload() throws Exception {
        var file = new MockMultipartFile("files", "doc.pdf", "application/pdf", "%PDF".getBytes());
        when(conversationService.attachFilesToConversation(isNull(), eq(USER), anyList())).thenReturn(
                new ConversationAttachments("new-conv",
                        List.of(new Attachment(1L, "doc.pdf", 4L, AttachmentStatus.PENDING, null))));

        mockMvc.perform(multipart("/api/conversations/attachments").file(file).principal(authentication))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.conversationAttachments.conversationId").value("new-conv"))
                .andExpect(jsonPath("$.conversationAttachments.attachments[0].filename").value("doc.pdf"));
    }

    @Test
    @DisplayName("upload refusé : code d'erreur explicite renvoyé par ExceptionsHandler")
    void attachmentUpload_rejected() throws Exception {
        var file = new MockMultipartFile("files", "doc.docx", "application/octet-stream", "x".getBytes());
        when(conversationService.attachFilesToConversation(eq("conv"), eq(USER), anyList())).thenThrow(
                new UploadException("doc.docx", UploadErrorCode.FILE_TYPE_UNSUPPORTED, "unsupported"));

        var response = mockMvc.perform(multipart("/api/conversations/attachments").file(file)
                        .param("conversationId", "conv").principal(authentication))
                .andReturn().getResponse();

        assertEquals(UploadErrorCode.FILE_TYPE_UNSUPPORTED.getHttpStatus().value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("FILE_TYPE_UNSUPPORTED"), response.getContentAsString());
    }

    @Test
    @DisplayName("polling du statut agrégé des pièces jointes")
    void attachmentsStatus() throws Exception {
        when(conversationService.getAttachmentsStatus("conv", USER)).thenReturn(AttachmentUploadResponse.fromAttachments(
                new ConversationAttachments("conv", List.of(
                        new Attachment(1L, "a.pdf", 1L, AttachmentStatus.PROCESSED, null),
                        new Attachment(2L, "b.pdf", 1L, AttachmentStatus.FAILED, UploadErrorCode.OCR_FAILED)))));

        mockMvc.perform(get("/api/conversations/conv/attachments").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("suppression d'une pièce jointe")
    void attachmentDelete() throws Exception {
        mockMvc.perform(delete("/api/conversations/attachments/7").principal(authentication))
                .andExpect(status().isOk());

        verify(conversationService).deleteAttachment(7L, USER);
    }
}
