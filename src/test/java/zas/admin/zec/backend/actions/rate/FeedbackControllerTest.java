package zas.admin.zec.backend.actions.rate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackControllerTest {

    private final UserService userService = mock(UserService.class);
    private final FeedbackService feedbackService = mock(FeedbackService.class);
    private final TestingAuthenticationToken principal = new TestingAuthenticationToken("jdoe", null);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(userService.getUuid("jdoe")).thenReturn("uuid-1");
        mvc = MockMvcBuilders.standaloneSetup(new FeedbackController(userService, feedbackService)).build();
    }

    @Test
    void answerFeedback_isStoredForTheUserUuid() throws Exception {
        mvc.perform(post("/api/conversations/feedbacks").param("type", "answer").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"messageId\":\"m1\",\"isPositive\":false,\"comment\":\"faux\",\"category\":\"WRONG_ANSWER\"}"))
                .andExpect(status().isOk());

        verify(feedbackService).sendFeedback("uuid-1",
                new Feedback("c1", "m1", false, "faux", null, null, FeedbackCategory.WRONG_ANSWER));
    }

    @Test
    void sourceFeedback_isRoutedByTypeParameter() throws Exception {
        mvc.perform(post("/api/conversations/feedbacks").param("type", "source").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"messageId\":\"m1\",\"documentId\":\"d1\",\"isPositive\":true}"))
                .andExpect(status().isOk());

        verify(feedbackService).sendFeedback("uuid-1",
                new SourceFeedback("c1", "m1", "d1", true, null, null, null, null));
    }

    @Test
    void getSourceFeedbacks_returnsFeedbacksOfTheUser() throws Exception {
        when(feedbackService.getFeedbacks("uuid-1", "c1", "m1"))
                .thenReturn(List.of(new SourceFeedback("c1", "m1", "d1", true, null, null, null, null)));

        mvc.perform(get("/api/conversations/feedbacks").param("type", "source")
                        .param("conversationId", "c1").param("messageId", "m1").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value("d1"));
    }

    @Test
    void unknownType_isNotMapped() throws Exception {
        mvc.perform(post("/api/conversations/feedbacks").param("type", "other").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
