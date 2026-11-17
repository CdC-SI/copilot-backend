package zas.admin.zec.backend.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Les {@code ChatClient} construits via {@link ChatClientConfig} portent les observations et advisors communs. */
class ChatClientConfigTest {

    @Test
    void builtClientsAreObservedAndUseTheDefaultAdvisors() {
        var observed = new CopyOnWriteArrayList<String>();
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context context) {
                observed.add(context.getName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });

        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("bonjour")))));

        String answer = ChatClientConfig.builder(chatModel, registry).build()
                .prompt().user("salut").call().content();

        assertEquals("bonjour", answer);
        assertTrue(observed.contains("spring.ai.chat.client"), () -> "observations: " + observed);
        assertTrue(ChatClientConfig.defaultAdvisors().stream().anyMatch(SimpleLoggerAdvisor.class::isInstance));
    }
}
