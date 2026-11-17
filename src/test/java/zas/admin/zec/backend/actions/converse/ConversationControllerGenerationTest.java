package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.ExceptionsHandler;
import zas.admin.zec.backend.tools.SecurityLogging;

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
