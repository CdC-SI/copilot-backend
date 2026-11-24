package zas.admin.zec.backend.tools;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.config.ChatClientConfig;
import zas.admin.zec.backend.actions.workspace.WorkspaceDto;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.prompts.PromptId;
import zas.admin.zec.backend.prompts.PromptRegistry;
import zas.admin.zec.backend.rag.TurnUsage;
import zas.admin.zec.backend.rag.remote.ZiaRagDocumentRetriever;
import zas.admin.zec.backend.rag.token.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/** Appel du tool de recherche sans requête, et inférence de workspace une seule fois par tour. */
class RAGToolTest {

    @Test
    void missingQueryReturnsAnErrorMessageInsteadOfThrowing() {
        var ziaRag = mock(ZiaRagDocumentRetriever.class);
        var tool = new RAGTool(ChatClientConfig.builder(mock(ChatModel.class), ObservationRegistry.NOOP), mock(SourceResolver.class),
                mock(WorkspaceService.class), mock(WorkspaceInferenceMonitoringService.class), new PromptRegistry(), ziaRag);

        assertEquals(RAGTool.MISSING_QUERY, tool.searchSocialInsuranceDocumentation(null, new ToolContext(new HashMap<>())));
        assertEquals(RAGTool.MISSING_QUERY, tool.searchSocialInsuranceDocumentation("  ", new ToolContext(new HashMap<>())));
        verifyNoInteractions(ziaRag);
    }

    /** Le workspace déjà résolu dans le tour se relit dans la référence partagée par les appels du tour. */
    @Test
    void previouslyInferredWorkspaceReadsTheTurnSharedReference() {
        assertEquals("", RAGTool.previouslyInferredWorkspace(Map.of()));

        var unresolved = new AtomicReference<String>();
        assertEquals("", RAGTool.previouslyInferredWorkspace(referenceContext(unresolved)));

        var resolved = new AtomicReference<>("AFAC");
        assertEquals("AFAC", RAGTool.previouslyInferredWorkspace(referenceContext(resolved)));
    }

    /**
     * Plusieurs appels de recherche dans un même tour (le LLM agentique reformule la question à chaque
     * itération) ne déclenchent qu'une seule inférence de workspace : un appel LLM par tour, et non par
     * appel, avec le même workspace appliqué à toutes les recherches du tour.
     */
    @Test
    void workspaceIsInferredOncePerTurnNotOncePerCall() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("AFAC")))));

        var sourceResolver = mock(SourceResolver.class);
        when(sourceResolver.resolve("AFAC")).thenReturn(List.of("afac"));
        var workspaceService = mock(WorkspaceService.class);
        when(workspaceService.getDefaultWorkspace()).thenReturn("GENERAL");
        when(workspaceService.getAll()).thenReturn(List.of(workspace("AFAC"), workspace("RH")));
        var monitoring = mock(WorkspaceInferenceMonitoringService.class);

        var tool = new RAGTool(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), sourceResolver, workspaceService,
                monitoring, new PromptRegistry(), mock(ZiaRagDocumentRetriever.class));

        var resolvedWorkspace = new AtomicReference<String>();
        List<Token> emittedTokens = new CopyOnWriteArrayList<>();
        Sinks.Many<Token> statusSink = Sinks.many().unicast().onBackpressureBuffer();
        statusSink.asFlux().subscribe(emittedTokens::add);

        Map<String, Object> context = new HashMap<>();
        context.put(ToolContextKeys.CTX_USER_ID, "u1");
        context.put(ToolContextKeys.CTX_CONVERSATION_ID, "c1");
        context.put(ToolContextKeys.CTX_LANGUAGE, "fr");
        context.put(ToolContextKeys.CTX_ORIGINAL_QUESTION, "Quel est le montant maximal de la cotisation annuelle ?");
        context.put(ToolContextKeys.CTX_RESOLVED_WORKSPACE, resolvedWorkspace);
        context.put(ToolContextKeys.CTX_STATUS_SINK, statusSink);
        context.put(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, new ArrayList<Document>());

        for (var query : List.of("cotisation maximale assurance facultative AVS", "montant cotisation annuelle AVS AI maximum")) {
            assertTrue(tool.searchSocialInsuranceDocumentation(query, new ToolContext(context))
                    .contains("<no_documentation_found>"), "aucun document attendu pour ces requêtes");
        }

        verify(chatModel, times(1)).call(any(Prompt.class));
        verify(sourceResolver, times(2)).resolve("AFAC");
        verify(monitoring, times(1)).recordInference("u1", "c1",
                "Quel est le montant maximal de la cotisation annuelle ?", "AFAC");
        assertEquals("AFAC", resolvedWorkspace.get());
        assertEquals(1, emittedTokens.stream().filter(t -> t.content().startsWith("<workspace_inference>")).count());
    }

    /**
     * La recherche est déléguée à zia-rag : la requête du LLM telle quelle (sans réécriture ni extension), les
     * sources du workspace, l'utilisateur et son jeton ; aucun appel LLM hors inférence du workspace.
     */
    @Test
    void retrievalIsDelegatedToZiaRag() {
        var chatModel = mock(ChatModel.class);
        var sourceResolver = mock(SourceResolver.class);
        when(sourceResolver.resolve("AFAC")).thenReturn(List.of("afac"));
        var ziaRag = mock(ZiaRagDocumentRetriever.class);
        when(ziaRag.retrieve(any(), any(), any(), any())).thenReturn(List.of(Document.builder().id("d1")
                .text("Cotisation maximale : 530 francs.").metadata(Map.of("source", "afac")).score(0.9).build()));

        var tool = new RAGTool(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), sourceResolver,
                mock(WorkspaceService.class), mock(WorkspaceInferenceMonitoringService.class), new PromptRegistry(), ziaRag);

        var retrieved = new ArrayList<Document>();
        Map<String, Object> context = new HashMap<>();
        context.put(ToolContextKeys.CTX_USER_ID, "u1");
        context.put(ToolContextKeys.CTX_WORKSPACE, "AFAC");
        context.put(ToolContextKeys.CTX_USER_TOKEN, "Bearer jwt");
        context.put(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, retrieved);
        context.put(ToolContextKeys.CTX_CITATION_REFS, new zas.admin.zec.backend.rag.CitationRefs());

        String result = tool.searchSocialInsuranceDocumentation("Quelle est la cotisation maximale ?", new ToolContext(context));

        assertTrue(result.contains("Cotisation maximale : 530 francs."));
        assertTrue(result.contains("<document id=\"d1\" collection=\"documentation\" ref=\"1\">"), result);
        assertEquals(List.of("d1"), retrieved.stream().map(Document::getId).toList());
        verify(ziaRag).retrieve("Quelle est la cotisation maximale ?", List.of("afac"), "u1", "Bearer jwt");
        verifyNoInteractions(chatModel);
    }

    @Test
    void exhaustedBudgetSkipsTheSearch() {
        var ziaRag = mock(ZiaRagDocumentRetriever.class);
        var tool = new RAGTool(ChatClientConfig.builder(mock(ChatModel.class), ObservationRegistry.NOOP), mock(SourceResolver.class),
                mock(WorkspaceService.class), mock(WorkspaceInferenceMonitoringService.class), new PromptRegistry(), ziaRag);

        String result = tool.searchSocialInsuranceDocumentation("rente",
                new ToolContext(Map.of(ToolContextKeys.CTX_TOOL_CALL_BUDGET, new AtomicInteger(0))));

        assertEquals(ToolCallBudget.EXHAUSTED_MESSAGE, result);
        verifyNoInteractions(ziaRag);
    }

    /** Inférence du workspace : version du prompt et usage tracés, réponse du LLM nettoyée. */
    @Test
    void inferenceTracesThePromptVersionAndTheLlmUsage() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("  RH \n"))),
                ChatResponseMetadata.builder().model("qwen").usage(new DefaultUsage(500, 3)).build()));
        var workspaceService = mock(WorkspaceService.class);
        when(workspaceService.getDefaultWorkspace()).thenReturn("GENERAL");
        when(workspaceService.getAll()).thenReturn(List.of(workspace("AFAC"), workspace("RH"),
                new WorkspaceDto(3L, "CdC", " ", List.of(), List.of("cdc"), null, null)));
        var sourceResolver = mock(SourceResolver.class);
        var prompts = new PromptRegistry();
        var tool = new RAGTool(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), sourceResolver, workspaceService,
                mock(WorkspaceInferenceMonitoringService.class), prompts, mock(ZiaRagDocumentRetriever.class));
        List<TurnUsage.LlmCall> usage = new ArrayList<>();
        Map<String, String> versions = new HashMap<>();
        Map<String, Object> context = new HashMap<>();
        context.put(ToolContextKeys.CTX_LLM_USAGE, usage);
        context.put(ToolContextKeys.CTX_PROMPT_VERSIONS, versions);
        context.put(ToolContextKeys.CTX_LANGUAGE, "de");

        tool.searchSocialInsuranceDocumentation("Ferienanspruch", new ToolContext(context));

        verify(sourceResolver).resolve("RH");
        assertEquals(Map.of("workspace-inference/de", prompts.get(PromptId.WORKSPACE_INFERENCE, "de").version()), versions);
        assertEquals(1, usage.size());
        assertEquals("workspace_inference", usage.getFirst().purpose());
        assertEquals(500, usage.getFirst().promptTokens());
        var sent = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(sent.capture());
        String system = sent.getValue().getSystemMessage().getText();
        assertTrue(system.contains("- CdC : (pas de description)"), system);
        assertTrue(system.contains("- RH : Description de RH Exemples de questions : Question exemple"), system);
    }

    @Test
    void unknownEmptyOrFailedInferenceFallsBackToTheDefaultWorkspace() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Inconnu")))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("")))))
                .thenThrow(new IllegalStateException("LLM down"));
        var workspaceService = mock(WorkspaceService.class);
        when(workspaceService.getDefaultWorkspace()).thenReturn("GENERAL");
        when(workspaceService.getAll()).thenReturn(List.of(workspace("AFAC")));
        var sourceResolver = mock(SourceResolver.class);
        var monitoring = mock(WorkspaceInferenceMonitoringService.class);
        var tool = new RAGTool(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), sourceResolver, workspaceService,
                monitoring, new PromptRegistry(), mock(ZiaRagDocumentRetriever.class));

        for (int i = 0; i < 3; i++) {
            // Pas de référence partagée : chaque appel relance l'inférence.
            tool.searchSocialInsuranceDocumentation("question " + i, null);
        }

        verify(sourceResolver, times(3)).resolve("GENERAL");
        verify(chatModel, times(3)).call(any(Prompt.class));
        // Sans contexte : ni utilisateur ni question originale, rien n'est journalisé de façon exploitable.
        verify(monitoring, times(3)).recordInference("", "", "", "GENERAL");
    }

    @Test
    void withoutAnyWorkspaceTheDefaultIsUsedWithoutCallingTheLlm() {
        var chatModel = mock(ChatModel.class);
        var workspaceService = mock(WorkspaceService.class);
        when(workspaceService.getDefaultWorkspace()).thenReturn("GENERAL");
        when(workspaceService.getAll()).thenReturn(List.of());
        var sourceResolver = mock(SourceResolver.class);
        var resolved = new AtomicReference<String>();
        var tool = new RAGTool(ChatClientConfig.builder(chatModel, ObservationRegistry.NOOP), sourceResolver, workspaceService,
                mock(WorkspaceInferenceMonitoringService.class), new PromptRegistry(), mock(ZiaRagDocumentRetriever.class));

        tool.searchSocialInsuranceDocumentation("question", new ToolContext(referenceContext(resolved)));

        assertEquals("GENERAL", resolved.get());
        verify(sourceResolver).resolve("GENERAL");
        verifyNoInteractions(chatModel);
    }

    private static Map<String, Object> referenceContext(AtomicReference<String> resolvedWorkspace) {
        return Map.of(ToolContextKeys.CTX_RESOLVED_WORKSPACE, resolvedWorkspace);
    }

    private static WorkspaceDto workspace(String name) {
        return new WorkspaceDto(1L, name, "Description de " + name, List.of("Question exemple"),
                List.of(name.toLowerCase()), null, null);
    }
}
