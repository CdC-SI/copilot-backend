package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.workspace.WorkspaceDto;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.Bm25Properties;
import zas.admin.zec.backend.config.properties.QueryExpanderProperties;
import zas.admin.zec.backend.config.properties.QueryRewriterProperties;
import zas.admin.zec.backend.config.properties.RetrievingProperties;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;
import zas.admin.zec.backend.rag.token.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/** Appel du tool de recherche sans requête, et inférence de workspace une seule fois par tour. */
class RAGToolTest {

    @Test
    void missingQueryReturnsAnErrorMessageInsteadOfThrowing() {
        var vectorStore = mock(VectorStore.class);
        var tool = new RAGTool(mock(ChatModel.class), vectorStore, mock(UserService.class), mock(DocumentReranker.class),
                mock(RetrievingProperties.class), mock(JdbcTemplate.class), mock(SourceResolver.class),
                mock(WorkspaceService.class), mock(WorkspaceInferenceMonitoringService.class));

        assertEquals(RAGTool.MISSING_QUERY, tool.searchSocialInsuranceDocumentation(null, new ToolContext(new HashMap<>())));
        assertEquals(RAGTool.MISSING_QUERY, tool.searchSocialInsuranceDocumentation("  ", new ToolContext(new HashMap<>())));
        verifyNoInteractions(vectorStore);
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

        var tool = new RAGTool(chatModel, mock(VectorStore.class), mock(UserService.class), mock(DocumentReranker.class),
                retrievalWithoutQueryTransformation(), mock(JdbcTemplate.class), sourceResolver, workspaceService, monitoring);

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

    private static Map<String, Object> referenceContext(AtomicReference<String> resolvedWorkspace) {
        return Map.of(ToolContextKeys.CTX_RESOLVED_WORKSPACE, resolvedWorkspace);
    }

    /** Récupération sans réécriture ni extension de requête et sans BM25 : seule l'inférence appelle le LLM. */
    private static RetrievingProperties retrievalWithoutQueryTransformation() {
        return new RetrievingProperties(5, null, new QueryRewriterProperties(false),
                new QueryExpanderProperties(false, 0, false), new Bm25Properties(false, 0, null));
    }

    private static WorkspaceDto workspace(String name) {
        return new WorkspaceDto(1L, name, "Description de " + name, List.of("Question exemple"),
                List.of(name.toLowerCase()), null, null);
    }
}
