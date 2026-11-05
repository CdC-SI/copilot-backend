package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.properties.AgentProperties;
import zas.admin.zec.backend.config.properties.DocumentToolProperties;
import zas.admin.zec.backend.config.properties.LinkedProcessProperties;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.TurnStatus;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.UsedDocuments;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.token.TurnTraceToken;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;
import zas.admin.zec.backend.tools.DocumentTool;
import zas.admin.zec.backend.tools.LinkedProcessAttacher;
import zas.admin.zec.backend.tools.MetadataFormatter;
import zas.admin.zec.backend.tools.RAGTool;
import zas.admin.zec.backend.tools.WorkspaceInferenceMonitoringService;
import zas.admin.zec.backend.tools.document.BusinessProcessLookup;
import zas.admin.zec.backend.tools.document.DocumentLookup;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tour complet de {@link RAGChatService} avec un modèle simulé (sans appel de tool) : prompt envoyé au modèle,
 * retrait de {@code <turn_meta>}, sources affichées selon {@code used}, trace du tour.
 */
class RAGChatServiceTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final BusinessProcessLookup processLookup = mock(BusinessProcessLookup.class);
    private RAGChatService service = service(new AgentProperties("low", 0.0));

    private RAGChatService service(AgentProperties agentProperties) {
        return new RAGChatService(chatModel, mock(RAGTool.class), mock(DocumentTool.class),
                mock(ConversationAttachmentTool.class), mock(WorkspaceInferenceMonitoringService.class),
                new DocumentToolProperties(10), mock(AttachmentRepository.class),
                new LinkedProcessAttacher(processLookup, new LinkedProcessProperties(true, 1, 20_000)), internalUser(),
                agentProperties);
    }

    private static UserService internalUser() {
        var userService = mock(UserService.class);
        when(userService.hasAccessToInternalDocuments("user-1")).thenReturn(true);
        return userService;
    }

    private static final ConsultedSource PROCESS = new ConsultedSource("p", "business_process", "business_process",
            "Processus P", "https://bpanda/p", null, null, "2026.03.02", "Brouillon", "BP-1", null, List.of());
    private static final ConsultedSource CHUNK = new ConsultedSource("a", "documentation", null, "Mémento",
            "https://x/memento", "3", null, null, null, "D", "0", List.of());

    private static final List<Message> HISTORY = List.of(
            new Message(null, null, "conv", null, "fr", "Comment traiter un décès ?", "USER", null, null, LocalDateTime.now(), null),
            new Message(null, null, "conv", null, "fr", "Voici les étapes…", "LLM", List.of(), List.of(), LocalDateTime.now(), "CSC",
                    new TurnTrace(TurnStatus.ANSWER, List.of(PROCESS, CHUNK))));

    private static Question question(String workspace) {
        return new Question("Résume ta réponse.", "fr", workspace, "DETAILED", "COMPLETE", "conv", true);
    }

    private static ChatResponse chunk(String text) {
        if (text.startsWith("usage:")) {   // dernier chunk d'un appel : usage seul
            String[] t = text.substring(6).split("/");
            return new ChatResponse(List.of(new Generation(new AssistantMessage(""))),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(Integer.parseInt(t[0]), Integer.parseInt(t[1]))).build());
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private List<Token> answer(Question question, String... modelChunks) {
        return answer(question, HISTORY, modelChunks);
    }

    private List<Token> answer(Question question, List<Message> history, String... modelChunks) {
        // Options par défaut du modèle (ChatModelsConfig) : l'appel agent ne doit en écraser que ses propres réglages.
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("qwen").maxTokens(16384)
                .temperature(0.0).reasoningEffort("low").build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.fromArray(modelChunks).map(RAGChatServiceTest::chunk));
        return service.answer(question, "user-1", history).collectList().block(Duration.ofSeconds(10));
    }

    private Prompt sentPrompt() {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).stream(captor.capture());
        return captor.getValue();
    }

    private static String text(Prompt prompt, MessageType type) {
        var messages = prompt.getInstructions().stream().filter(m -> m.getMessageType() == type).toList();
        return messages.getLast().getText();
    }

    @Test
    void usedIdsDriveTheDisplayedSourcesAndTheTagIsStripped() {
        var tokens = answer(question(""), "Résumé en 3 points.", "\n<turn_meta status=\"answer\" used=\"p\"/>",
                "usage:2400/120");

        String visible = tokens.stream().filter(TextToken.class::isInstance).map(Token::content).collect(Collectors.joining());
        assertEquals("Résumé en 3 points.\n", visible);

        var sources = tokens.stream().filter(SourceToken.class::isInstance).map(SourceToken.class::cast).toList();
        assertEquals(List.of("p"), sources.stream().map(SourceToken::documentId).toList());

        var trace = tokens.stream().filter(TurnTraceToken.class::isInstance).map(t -> ((TurnTraceToken) t).trace())
                .findFirst().orElseThrow();
        assertEquals(TurnStatus.ANSWER, trace.status());
        // "p" vient d'un tour précédent : classé comme repris.
        assertEquals(List.of("p"), trace.used().declared());
        assertEquals(List.of("p"), trace.used().reused());
        assertTrue(trace.used().current().isEmpty());
        assertTrue(trace.sources().isEmpty());
        assertTrue(trace.toolCalls().isEmpty());
        // Usage relevé dans le flux : un appel LLM.
        assertEquals(1, trace.usage().agentCalls().size());
        assertEquals(2400, trace.usage().agentCalls().getFirst().promptTokens());
        assertEquals(1, trace.usage().toMap().get("llm_calls"));
    }

    @Test
    void forcedWorkspaceDirectiveGoesIntoTheCurrentUserMessageNotTheSystemPrompt() {
        answer(question("CSC"), "Réponse.<turn_meta status=\"answer\" used=\"\"/>");
        Prompt forced = sentPrompt();
        clearInvocations(chatModel);
        answer(question(""), "Réponse.<turn_meta status=\"answer\" used=\"\"/>");
        Prompt normal = sentPrompt();

        String expectedSystem = RAGPrompts.getAgenticSystemPrompt("fr").formatted("COMPLETE");
        assertEquals(expectedSystem, text(forced, MessageType.SYSTEM));
        assertEquals(text(normal, MessageType.SYSTEM), text(forced, MessageType.SYSTEM));

        String user = text(forced, MessageType.USER);
        assertTrue(user.startsWith("<retrieved_sources turn=\"1\">"), user);
        assertTrue(user.contains("<consigne_prioritaire>"), user);
        assertTrue(user.contains("« CSC »"), user);
        assertTrue(user.endsWith("</consigne_prioritaire>\n\nRésume ta réponse."), user);
        assertFalse(text(normal, MessageType.USER).contains("<consigne_prioritaire>"));
    }

    /**
     * Après une question de suivi de l'agent, le processus lu au tour précédent est relu par id et joint au message
     * courant ({@code <linked_processes>}, après {@code <retrieved_sources>}) : il compte parmi les documents du tour.
     */
    @Test
    void processReadBeforeAFollowUpQuestionIsAttachedToTheCurrentMessage() {
        var process = new Document("p", "{\"nodes\":[]}", Map.of(MetadataFormatter.META_COLLECTION, "business_process",
                "process_id", "BP-1", "process_name", "Processus P"));
        when(processLookup.find(any())).thenReturn(new DocumentLookup.LookupResult(List.of(process), false, null));
        var followUp = List.of(HISTORY.getFirst(),
                new Message(null, null, "conv", null, "fr", "Quelle est sa nationalité ?", "LLM", List.of(), List.of(),
                        LocalDateTime.now(), "CSC", new TurnTrace(TurnStatus.FOLLOWUP, List.of(PROCESS, CHUNK))));

        var tokens = answer(question(""), followUp, "Réponse.<turn_meta status=\"answer\" used=\"p\"/>");

        String user = text(sentPrompt(), MessageType.USER);
        assertTrue(user.startsWith("<retrieved_sources turn=\"1\">"), user);
        assertTrue(user.contains("</retrieved_sources>\n\n<linked_processes>"), user);
        assertTrue(user.contains("<linked_process>" + System.lineSeparator() + MetadataFormatter.format(process, true, true)), user);
        assertTrue(user.endsWith("</linked_processes>\n\nRésume ta réponse."), user);
        var trace = tokens.stream().filter(TurnTraceToken.class::isInstance).map(t -> ((TurnTraceToken) t).trace())
                .findFirst().orElseThrow();
        assertEquals(List.of("p"), trace.sources().stream().map(ConsultedSource::id).toList());
        assertEquals(List.of("p"), trace.used().current());
    }

    @Test
    void noProcessIsAttachedAfterAnAnswer() {
        answer(question(""), "Réponse.<turn_meta status=\"answer\" used=\"\"/>");

        assertFalse(text(sentPrompt(), MessageType.USER).contains("<linked_processes>"));
        verifyNoInteractions(processLookup);
    }

    /**
     * Réglages de l'agent (reasoning, température) : appliqués à l'appel agent par-dessus les options par
     * défaut du modèle, qui restent (modèle, maxTokens), et tracés avec l'usage du tour.
     */
    @Test
    void agentSettingsApplyToTheAgentCallAndAreTraced() {
        service = service(new AgentProperties("medium", 1.0));

        var tokens = answer(question(""), "Réponse.<turn_meta status=\"answer\" used=\"\"/>", "usage:100/10");

        Prompt prompt = sentPrompt();
        var options = assertInstanceOf(OpenAiChatOptions.class, prompt.getOptions());
        assertEquals("medium", options.getReasoningEffort());
        assertEquals(1.0, options.getTemperature());
        assertEquals("qwen", options.getModel());
        assertEquals(16384, options.getMaxTokens());
        assertEquals(RAGPrompts.getAgenticSystemPrompt("fr").formatted("COMPLETE"), text(prompt, MessageType.SYSTEM));
        var trace = tokens.stream().filter(TurnTraceToken.class::isInstance).map(t -> ((TurnTraceToken) t).trace())
                .findFirst().orElseThrow();
        assertEquals(Map.of("prompt_version", RAGPrompts.AGENTIC_PROMPT_VERSION, "reasoning_effort", "medium", "temperature", 1.0),
                trace.usage().toMap().get("agent_settings"));
    }

    /** Une valeur invalide dans le {@code .env} (ex. commentaire en fin de ligne) bloque le démarrage. */
    @Test
    void invalidReasoningEffortFailsAtStartup() {
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(AgentPropertiesConfig.class)
                .withPropertyValues("rag.agent.temperature=1.0");
        runner.withPropertyValues("rag.agent.reasoning-effort=medium").run(context -> {
            assertThat(context).hasNotFailed();
            assertEquals("medium", context.getBean(AgentProperties.class).reasoningEffort());
        });
        runner.withPropertyValues("rag.agent.reasoning-effort=medium // possible values: low, medium, xhigh")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(AgentProperties.class)
    static class AgentPropertiesConfig {}

    /**
     * Une réponse peut exposer ce qui est établi puis poser une question (status answer) : le processus qu'elle cite
     * dans used est rejoint au tour suivant, comme après une question de suivi.
     */
    @Test
    void processUsedByThePreviousAnswerIsAttachedToTheCurrentMessage() {
        var process = new Document("p", "{\"nodes\":[]}", Map.of(MetadataFormatter.META_COLLECTION, "business_process",
                "process_id", "BP-1", "process_name", "Processus P"));
        when(processLookup.find(any())).thenReturn(new DocumentLookup.LookupResult(List.of(process), false, null));
        var history = List.of(HISTORY.getFirst(),
                new Message(null, null, "conv", null, "fr", "Il remplit les conditions établies. Quel est son âge ?", "LLM",
                        List.of(), List.of(), LocalDateTime.now(), "CSC", new TurnTrace(TurnStatus.ANSWER,
                        List.of(PROCESS, CHUNK), new UsedDocuments(List.of("p", "a"), List.of("p", "a"), List.of(), List.of()),
                        List.of())));

        answer(question(""), history, "Réponse.<turn_meta status=\"answer\" used=\"p\"/>");

        assertTrue(text(sentPrompt(), MessageType.USER).contains("<linked_processes>"));
    }
}
