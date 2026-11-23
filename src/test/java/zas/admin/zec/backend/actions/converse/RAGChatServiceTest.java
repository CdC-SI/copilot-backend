package zas.admin.zec.backend.actions.converse;

import zas.admin.zec.backend.config.properties.ToolApprovalProperties;
import zas.admin.zec.backend.tools.ToolApprovalService;
import io.micrometer.observation.ObservationRegistry;
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
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.config.ChatClientConfig;
import zas.admin.zec.backend.config.properties.DocumentToolProperties;
import zas.admin.zec.backend.config.properties.MemoryProperties;
import zas.admin.zec.backend.persistence.repository.ConversationSummaryRepository;
import org.springframework.context.ApplicationEventPublisher;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.prompts.PromptId;
import zas.admin.zec.backend.prompts.PromptRegistry;
import zas.admin.zec.backend.rag.CitationRefs;
import zas.admin.zec.backend.rag.CitationStreamFilter;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.TurnStatus;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.token.CitationToken;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.token.TurnTraceToken;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;
import zas.admin.zec.backend.tools.DocumentTool;
import zas.admin.zec.backend.tools.RAGTool;
import zas.admin.zec.backend.tools.WorkspaceInferenceMonitoringService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tour complet de {@link RAGChatService} avec un modèle simulé (sans appel de tool) : prompt envoyé au modèle,
 * retrait de {@code <turn_meta>}, sources affichées selon {@code used}, trace du tour.
 */
class RAGChatServiceTest {

    private static final PromptRegistry PROMPTS = new PromptRegistry();

    private final ChatModel chatModel = mock(ChatModel.class);
    private final RAGChatService service = new RAGChatService(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), mock(RAGTool.class), mock(DocumentTool.class),
            mock(ConversationAttachmentTool.class), mock(WorkspaceInferenceMonitoringService.class),
            new DocumentToolProperties(10), mock(AttachmentRepository.class),
            new ToolApprovalService(new ToolApprovalProperties(List.of(), Duration.ofSeconds(1))), PROMPTS,
            new ConversationMemory(new MemoryProperties(12000, 0.6, true), mock(ConversationSummaryRepository.class),
                   ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), PROMPTS, mock(ApplicationEventPublisher.class)));

    private static final ConsultedSource PROCESS = new ConsultedSource("p", "business_process", "business_process",
            "Processus P", "https://bpanda/p", null, null, "2026.03.02", "Brouillon", "BP-1", null, List.of());
    private static final ConsultedSource CHUNK = new ConsultedSource("a", "documentation", null, "Mémento",
            "https://x/memento", "3", null, null, null, "D", "0", List.of());

    private static final List<Message> HISTORY = List.of(
            new Message(null, null, "conv", null, "fr", "Comment traiter un décès ?", "USER", null, null, LocalDateTime.now(), null),
            new Message(null, null, "conv", null, "fr", "Voici les étapes…", "LLM", List.of(), List.of(), LocalDateTime.now(), "CSC",
                    new TurnTrace(TurnStatus.ANSWER, List.of(PROCESS, CHUNK))));

    private static Question question(String workspace) {
        return new Question("Résume ta réponse.", "fr", workspace, "DETAILED", "COMPLETE", "conv", true, null);
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
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.fromArray(modelChunks).map(RAGChatServiceTest::chunk));
        return service.answer(question, "user-1", HISTORY).collectList().block(Duration.ofSeconds(10));
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
                .filter(t -> t.status() == TurnStatus.ANSWER)
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
        // Version du prompt système tracée ; pas de consigne de workspace (non choisi par l'utilisateur).
        assertEquals(Map.of("agentic-system/fr", PROMPTS.get(PromptId.AGENTIC_SYSTEM, "fr").version()), trace.prompts());
    }

    @Test
    void bracketsAreKeptWhenNoExtractWasReadAndNewCitationsPrecedeTheirText() {
        var tokens = answer(question(""), "Voir [1].", "<turn_meta status=\"answer\" used=\"\"/>");
        String visible = tokens.stream().filter(TextToken.class::isInstance).map(Token::content).collect(Collectors.joining());
        assertEquals("Voir [1].", visible);

        var refs = new CitationRefs();
        refs.refFor("a");
        var filter = new CitationStreamFilter(refs, ref -> refs.idFor(ref).map(id -> CHUNK));
        var emitted = RAGChatService.textWithCitations(filter.accept("Délai [1]."), filter);

        assertEquals(2, emitted.size());
        var citation = assertInstanceOf(CitationToken.class, emitted.getFirst());
        assertEquals(1, citation.number());
        assertEquals(1, citation.source().citation());
        assertEquals("a", citation.source().documentId());
        assertEquals("Délai [^1].", emitted.get(1).content());
        assertEquals(1, RAGChatService.textWithCitations("suite", filter).size());
        // Texte retenu par le filtre (marqueur incomplet) : pas de jeton vide.
        assertTrue(RAGChatService.textWithCitations(filter.accept("[1"), filter).isEmpty());
    }

    @Test
    void forcedWorkspaceDirectiveGoesIntoTheCurrentUserMessageNotTheSystemPrompt() {
        var forcedTokens = answer(question("CSC"), "Réponse.<turn_meta status=\"answer\" used=\"\"/>");
        Prompt forced = sentPrompt();
        clearInvocations(chatModel);
        answer(question(""), "Réponse.<turn_meta status=\"answer\" used=\"\"/>");
        Prompt normal = sentPrompt();

        String expectedSystem = PROMPTS.get(PromptId.AGENTIC_SYSTEM, "fr").render("response_format", "COMPLETE");
        assertEquals(expectedSystem, text(forced, MessageType.SYSTEM));
        assertEquals(text(normal, MessageType.SYSTEM), text(forced, MessageType.SYSTEM));

        String user = text(forced, MessageType.USER);
        assertTrue(user.startsWith("<retrieved_sources turn=\"1\">"), user);
        assertTrue(user.contains("<consigne_prioritaire>"), user);
        assertTrue(user.contains("« CSC »"), user);
        assertTrue(user.endsWith("</consigne_prioritaire>\n\nRésume ta réponse."), user);
        assertFalse(text(normal, MessageType.USER).contains("<consigne_prioritaire>"));

        var forcedTrace = forcedTokens.stream().filter(TurnTraceToken.class::isInstance)
                .map(t -> ((TurnTraceToken) t).trace()).findFirst().orElseThrow();
        assertEquals(Set.of("agentic-system/fr", "forced-retrieval-directive/fr"), forcedTrace.prompts().keySet());
    }
}
