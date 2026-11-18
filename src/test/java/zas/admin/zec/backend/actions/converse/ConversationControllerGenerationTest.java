package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.ExceptionsHandler;
import zas.admin.zec.backend.config.security.UserToken;
import zas.admin.zec.backend.tools.SecurityLogging;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F3b : endpoint Stop et refus (409) d'une génération, renvoyés avant l'ouverture du flux SSE. */
class ConversationControllerGenerationTest {

    private static final String USER = "user";

    private final UserService userService = mock(UserService.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final TestingAuthenticationToken authentication = new TestingAuthenticationToken("jdoe", null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(userService.getUuid("jdoe")).thenReturn(USER);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConversationController(userService, conversationService, mock(SecurityLogging.class)))
                .setControllerAdvice(new ExceptionsHandler())
                .build();
    }

    @Test
    @DisplayName("Stop d'une génération en cours : 202")
    void stop_accepted() throws Exception {
        when(conversationService.stopGeneration(USER, "conv", "m1")).thenReturn(true);

        mockMvc.perform(post("/api/conversations/conv/generations/m1/stop").principal(authentication))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Stop d'une génération inconnue ou terminée : 404")
    void stop_unknown() throws Exception {
        mockMvc.perform(post("/api/conversations/conv/generations/m1/stop").principal(authentication))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T1 : approbation d'un appel de tool : 204")
    void approval_approved() throws Exception {
        when(conversationService.decideToolApproval(USER, "conv", "appr-1", true)).thenReturn(true);

        mockMvc.perform(post("/api/conversations/conv/approvals/appr-1").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("T1 : refus d'un appel de tool : 204")
    void approval_rejected() throws Exception {
        when(conversationService.decideToolApproval(USER, "conv", "appr-1", false)).thenReturn(true);

        mockMvc.perform(post("/api/conversations/conv/approvals/appr-1").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("T1 : demande inconnue, tranchée ou d'un autre utilisateur : 404")
    void approval_unknown() throws Exception {
        mockMvc.perform(post("/api/conversations/conv/approvals/appr-1").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("T1 : décision absente : 400")
    void approval_missingDecision() throws Exception {
        mockMvc.perform(post("/api/conversations/conv/approvals/appr-1").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T2 : le jeton Blue de l'utilisateur est posé dans le contexte Reactor du flux de réponse")
    void askQuestion_putsUserTokenInReactorContext() {
        when(conversationService.streamAnswer(any(), eq(USER))).thenReturn(Flux.deferContextual(context ->
                Flux.just(ChatStreamEvent.textDelta(context.getOrDefault(UserToken.CONTEXT_KEY, "absent")))));
        var jwt = Jwt.withTokenValue("jwt-value").header("alg", "none").claim("sub", "jdoe").build();
        var controller = new ConversationController(userService, conversationService, mock(SecurityLogging.class));

        var event = controller.askQuestion(new Question("Question ?", null, null, null, null, "conv", true, null),
                new JwtAuthenticationToken(jwt)).blockFirst();

        assertTrue(event.data().contains("Bearer jwt-value"), event.data());
    }

    @Test
    @DisplayName("génération refusée : 409 JSON avec le code, malgré Accept: text/event-stream")
    void askQuestion_conflict_returnsJson409() throws Exception {
        when(conversationService.streamAnswer(any(), eq(USER))).thenThrow(
                new ConversationConflictException(ConversationConflictException.TURN_NOT_REPLACEABLE));

        mockMvc.perform(multipart("/api/conversations")
                        .param("query", "Question ?")
                        .param("conversationId", "conv")
                        .param("replaceMessageId", "a1")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.MESSAGE").value(ConversationConflictException.TURN_NOT_REPLACEABLE));
    }
}
