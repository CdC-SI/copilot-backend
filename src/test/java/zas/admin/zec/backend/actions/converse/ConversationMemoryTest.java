package zas.admin.zec.backend.actions.converse;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.context.ApplicationEventPublisher;
import zas.admin.zec.backend.config.ChatClientConfig;
import zas.admin.zec.backend.config.properties.MemoryProperties;
import zas.admin.zec.backend.observability.TurnUsageEvent;
import zas.admin.zec.backend.persistence.repository.ConversationSummaryRepository;
import zas.admin.zec.backend.persistence.repository.ConversationSummaryRepository.ConversationSummary;
import zas.admin.zec.backend.prompts.PromptId;
import zas.admin.zec.backend.prompts.PromptRegistry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationMemoryTest {

    private static final PromptRegistry PROMPTS = new PromptRegistry();

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private ConversationMemory memory(int budget, double ratio) {
        return new ConversationMemory(new MemoryProperties(budget, ratio, true), summaries,
                ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), PROMPTS, eventPublisher);
    }

    private static Message user(String id, String text) {
        return new Message(id, "user-1", "conv", null, "fr", text, "USER", null, null, LocalDateTime.now(), null);
    }

    private static Message assistant(String id, String text) {
        return new Message(id, "user-1", "conv", null, "fr", text, "LLM", List.of(), List.of(), LocalDateTime.now(), null);
    }

    /** Trois tours : q1/a1, q2/a2, q3/a3. */
    private static List<Message> history() {
        return List.of(user("u1", "Première question sur les rentes AVS."), assistant("a1", "Réponse un."),
                user("u2", "Deuxième question sur les allocations familiales."), assistant("a2", "Réponse deux."),
                user("u3", "Troisième question sur les PC."), assistant("a3", "Réponse trois."));
    }

    private static List<String> texts(List<org.springframework.ai.chat.messages.Message> messages) {
        return messages.stream().map(org.springframework.ai.chat.messages.Message::getText).toList();
    }

    private static ConversationSummary summary(String coveredMessageId) {
        return new ConversationSummary("user-1", "conv", coveredMessageId, "- L'utilisateur a demandé X.",
                "conversation-summary/fr@0123456789ab", 100, 20, LocalDateTime.now());
    }

    @Test
    void firstTurnDropsTheOldestTurnsOverBudgetButKeepsTheLastOne() {
        assertEquals(0, ConversationMemory.firstTurn(List.of(10, 10, 10), 0, 0, 30));
        assertEquals(1, ConversationMemory.firstTurn(List.of(10, 10, 10), 0, 0, 29));
        assertEquals(2, ConversationMemory.firstTurn(List.of(10, 10, 10), 0, 5, 20));
        assertEquals(2, ConversationMemory.firstTurn(List.of(10, 10, 50), 0, 0, 20));
        // Tours déjà résumés : jamais réintégrés.
        assertEquals(1, ConversationMemory.firstTurn(List.of(10, 10, 10), 1, 5, 100));
    }

    @Test
    void compactionEndSummarizesUntilTheTargetWithinTheChunkLimit() {
        assertEquals(2, ConversationMemory.compactionEnd(List.of(10, 10, 10, 10), 0, 20, 100));
        assertEquals(3, ConversationMemory.compactionEnd(List.of(10, 10, 10, 10), 0, 0, 100));
        // Rattrapage progressif : au plus 15 tokens résumés en une fois, mais au moins un tour.
        assertEquals(1, ConversationMemory.compactionEnd(List.of(10, 10, 10, 10), 0, 0, 15));
        assertEquals(2, ConversationMemory.compactionEnd(List.of(50, 10, 10, 10), 1, 0, 15));
        assertEquals(1, ConversationMemory.compactionEnd(List.of(10, 10, 10, 10), 1, 30, 100));
    }

    @Test
    void historyWithinBudgetIsSentUnchanged() {
        var history = history();
        var rendered = AbstractChatService.toPromptMessages(history);

        var window = memory(10_000, 0.6).window("user-1", "conv", history, rendered, new HashMap<>());

        assertEquals(texts(rendered), texts(window));
    }

    @Test
    void historyOverBudgetKeepsOnlyTheMostRecentCompleteTurns() {
        var history = history();

        var window = memory(1, 0.6).window("user-1", "conv", history, AbstractChatService.toPromptMessages(history), null);

        assertEquals(List.of("Troisième question sur les PC.", "Réponse trois."), texts(window));
    }

    @Test
    void persistedSummaryReplacesTheTurnsItCoversAndItsPromptVersionIsTraced() {
        when(summaries.find("user-1", "conv")).thenReturn(Optional.of(summary("a1")));
        var history = history();
        Map<String, String> versions = new HashMap<>();

        var window = memory(10_000, 0.6).window("user-1", "conv", history, AbstractChatService.toPromptMessages(history),
                versions);

        assertEquals(4, window.size());
        assertInstanceOf(UserMessage.class, window.getFirst());
        assertEquals("<conversation_summary>\n- L'utilisateur a demandé X.\n</conversation_summary>\n\n"
                + "Deuxième question sur les allocations familiales.", window.getFirst().getText());
        assertEquals(Map.of("conversation-summary/fr", "0123456789ab"), versions);
    }

    @Test
    void summaryWhoseLastCoveredMessageNoLongerExistsIsIgnored() {
        when(summaries.find("user-1", "conv")).thenReturn(Optional.of(summary("deleted")));
        var history = history();
        var rendered = AbstractChatService.toPromptMessages(history);

        assertEquals(texts(rendered), texts(memory(10_000, 0.6).window("user-1", "conv", history, rendered, null)));
    }

    private void llmAnswers(String text) {
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().model("qwen").usage(new DefaultUsage(300, 40)).build()));
    }

    private String sentRequest() {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return captor.getValue().getUserMessage().getText();
    }

    @Test
    void historyWithinBudgetIsNotSummarized() {
        assertFalse(memory(10_000, 0.6).compact("user-1", "conv", "fr", history()));

        verifyNoInteractions(chatModel);
        verify(summaries, never()).save(any());
    }

    @Test
    void oldestTurnsAreSummarizedOneChunkAtATimeAndTheUsageIsPublished() {
        llmAnswers("  - Question 1 : rentes AVS.  ");

        // Budget minimal : résumé limité à un tour par compaction (rattrapage progressif).
        assertTrue(memory(1, 0.5).compact("user-1", "conv", "fr", history()));

        String request = sentRequest();
        String expected = PROMPTS.get(PromptId.CONVERSATION_SUMMARY, "fr").render("previous_summary", "",
                "turns", "<user>\nPremière question sur les rentes AVS.\n</user>\n<assistant>\nRéponse un.\n</assistant>");
        assertEquals(expected, request);

        var saved = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(summaries).save(saved.capture());
        assertEquals("a1", saved.getValue().coveredMessageId());
        assertEquals("- Question 1 : rentes AVS.", saved.getValue().summary());
        assertEquals("conversation-summary/fr@" + PROMPTS.get(PromptId.CONVERSATION_SUMMARY, "fr").version(),
                saved.getValue().promptVersion());
        assertEquals(300, saved.getValue().promptTokens());

        var event = ArgumentCaptor.forClass(TurnUsageEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(ConversationMemory.SUMMARY_PURPOSE, event.getValue().usage().auxiliaryCalls().getFirst().purpose());
    }

    @Test
    void summaryIsIncrementalAndNeverCoversTheLastTurn() {
        when(summaries.find("user-1", "conv")).thenReturn(Optional.of(summary("a1")));
        llmAnswers("- Résumé mis à jour.");

        assertTrue(memory(1, 0.5).compact("user-1", "conv", "fr", history()));

        assertTrue(sentRequest().contains("<resume_precedent>\n- L'utilisateur a demandé X.\n</resume_precedent>"));
        assertTrue(sentRequest().contains("<user>\nDeuxième question sur les allocations familiales.\n</user>"));
        assertFalse(sentRequest().contains("Première question"));
        var saved = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(summaries).save(saved.capture());
        assertEquals("a2", saved.getValue().coveredMessageId());

        // Seul le dernier tour reste : rien à résumer.
        clearInvocations(chatModel, summaries);
        when(summaries.find("user-1", "conv")).thenReturn(Optional.of(summary("a2")));
        assertFalse(memory(1, 0.5).compact("user-1", "conv", "fr", history()));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void blankSummaryIsNotSaved() {
        llmAnswers("   ");

        assertFalse(memory(1, 0.5).compact("user-1", "conv", "fr", history()));

        verify(summaries, never()).save(any());
    }

    @Test
    void deletionInvalidatesAnInFlightCompactionWithoutWaitingForTheModel() throws Exception {
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("- Résumé."))),
                ChatResponseMetadata.builder().model("qwen").usage(new DefaultUsage(300, 40)).build());
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return response;
        });
        var memory = memory(1, 0.5);

        memory.compactAsync("user-1", "conv", "fr", ConversationMemoryTest::history);
        assertTrue(started.await(5, TimeUnit.SECONDS));

        var executor = Executors.newSingleThreadExecutor();
        try {
            var deletion = executor.submit(() -> memory.forget("user-1", "conv"));
            deletion.get(5, TimeUnit.SECONDS);
            verify(summaries).delete("user-1", "conv");
            release.countDown();
        } finally {
            executor.shutdownNow();
        }

        verify(summaries, after(5_000).never()).save(any(ConversationSummary.class));
    }

    @Test
    void initialMessagesWithoutQuestionBelongToTheFirstTurn() {
        var history = new ArrayList<Message>();
        history.add(assistant("faq", "Bienvenue"));
        history.addAll(history());

        assertEquals(List.of(0, 3, 5), ConversationMemory.turnStarts(history));
    }
}
