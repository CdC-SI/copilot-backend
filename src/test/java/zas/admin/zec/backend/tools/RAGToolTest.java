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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.workspace.WorkspaceDto;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.Bm25Properties;
import zas.admin.zec.backend.config.properties.LinkedProcessProperties;
import zas.admin.zec.backend.config.properties.QueryExpanderProperties;
import zas.admin.zec.backend.config.properties.QueryRewriterProperties;
import zas.admin.zec.backend.config.properties.RetrievingProperties;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.RAGTool.LinkedSource;
import zas.admin.zec.backend.tools.document.BusinessProcessLookup;
import zas.admin.zec.backend.tools.document.DocumentCollection;
import zas.admin.zec.backend.tools.document.DocumentLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Appel du tool de recherche sans requête, blocs {@code <linked_processes>} et {@code <linked_sources>}, et inférence de
 * workspace une seule fois par tour.
 */
class RAGToolTest {

    private static final String ADHESION = "CSC AF-61-1-100";

    @Test
    void missingQueryReturnsAnErrorMessageInsteadOfThrowing() {
        var vectorStore = mock(VectorStore.class);
        var tool = new RAGTool(mock(ChatModel.class), vectorStore, mock(UserService.class), mock(DocumentReranker.class),
                mock(RetrievingProperties.class), mock(JdbcTemplate.class), mock(SourceResolver.class),
                mock(WorkspaceService.class), mock(WorkspaceInferenceMonitoringService.class), mock(LinkedProcessAttacher.class));

        assertEquals(RAGTool.MISSING_QUERY, tool.searchSocialInsuranceDocumentation(null, new ToolContext(new HashMap<>())));
        assertEquals(RAGTool.MISSING_QUERY, tool.searchSocialInsuranceDocumentation("  ", new ToolContext(new HashMap<>())));
        verifyNoInteractions(vectorStore);
    }

    @Test
    void linkedSourcesListProcessesAndDocumentsOnceInRelevanceOrder() {
        var aideMemoire = chunk("a1", Map.of("doc_id", "AM-1", "outgoing_links", List.of(
                Map.of("type", "process", "title", "CSC AF - Traiter les demandes d'adhésion", "process_id", ADHESION),
                Map.of("type", "document", "title", " Pas \"immatriculé\"\n", "target_doc_id", "AM-12"),
                Map.of("type", "process", "title", "Carte des processus", "url", "https://bpanda/map"))));
        var description = chunk("d1", Map.of("doc_type", "business_process", "process_id", "CSC AF-61-2-408",
                "process_name", "CSC AF - Exclure un assuré"));
        var otherAideMemoire = chunk("a2", Map.of("outgoing_links", List.of(
                Map.of("process_id", ADHESION), Map.of("target_doc_id", "AM-12"))));

        var sources = RAGTool.linkedSources(List.of(aideMemoire, description, otherAideMemoire), List.of());

        assertEquals(List.of(
                new LinkedSource("a1", DocumentCollection.BUSINESS_PROCESS, "process_id", ADHESION,
                        "CSC AF - Traiter les demandes d'adhésion"),
                new LinkedSource("a1", DocumentCollection.DOCUMENTATION, "doc_id", "AM-12", "Pas 'immatriculé'"),
                new LinkedSource("d1", DocumentCollection.BUSINESS_PROCESS, "process_id", "CSC AF-61-2-408",
                        "CSC AF - Exclure un assuré")), sources);
    }

    @Test
    void targetsAlreadyReadDuringTheTurnAreExcluded() {
        var aideMemoire = chunk("a1", Map.of("outgoing_links", List.of(
                Map.of("process_id", ADHESION), Map.of("target_doc_id", "AM-12"), Map.of("target_doc_id", "AM-13"))));
        var process = new Document("p1", "{\"nodes\":[]}", Map.of(MetadataFormatter.META_COLLECTION, "business_process",
                "process_id", ADHESION));
        var linkedChunk = chunk("c1", Map.of("doc_id", "AM-12"));

        var sources = RAGTool.linkedSources(List.of(aideMemoire), List.of(aideMemoire, process, linkedChunk));

        assertEquals(List.of("AM-13"), sources.stream().map(LinkedSource::value).toList());
    }

    @Test
    void linkedSourcesAreLimitedPerCollection() {
        var links = IntStream.rangeClosed(1, DocumentTool.MAX_DOCUMENTS_PER_CALL + 2)
                .mapToObj(i -> List.of(Map.of("process_id", "P-" + i), Map.of("target_doc_id", "D-" + i)))
                .flatMap(List::stream).toList();

        var sources = RAGTool.linkedSources(List.of(chunk("a1", Map.of("outgoing_links", links))), List.of());

        assertEquals(DocumentTool.MAX_DOCUMENTS_PER_CALL,
                sources.stream().filter(s -> s.collection() == DocumentCollection.BUSINESS_PROCESS).count());
        assertEquals(DocumentTool.MAX_DOCUMENTS_PER_CALL,
                sources.stream().filter(s -> s.collection() == DocumentCollection.DOCUMENTATION).count());
    }

    @Test
    void blockListsTargetsAsDataWithoutInstruction() {
        String block = RAGTool.formatLinkedSources(List.of(
                new LinkedSource("a1", DocumentCollection.BUSINESS_PROCESS, "process_id", ADHESION, "Adhésion"),
                new LinkedSource("a1", DocumentCollection.DOCUMENTATION, "doc_id", "AM-12", null)));

        String nl = System.lineSeparator();
        assertEquals(nl + "<linked_sources>" + nl
                + "- from=a1; collection=business_process; process_id=" + ADHESION + "; title=\"Adhésion\"" + nl
                + "- from=a1; collection=documentation; doc_id=AM-12" + nl
                + "</linked_sources>", block);
        assertEquals("", RAGTool.formatLinkedSources(List.of()));
        assertTrue(RAGTool.linkedSources(List.of(chunk("m1", Map.of("title", "Mémento"))), List.of()).isEmpty());
    }

    /**
     * Recherche dont le meilleur résultat renvoie à un processus : le processus est joint (contenu) en fin de résultat
     * et déposé avec les documents du tour, et seules les autres cibles restent dans {@code <linked_sources>}.
     * Interrupteur coupé : le processus n'est que listé, comme avant.
     */
    @Test
    void linkedProcessIsAttachedToTheSearchResultAndLeavesLinkedSources() {
        var aideMemoire = Document.builder().id("a1").text("Adhésion : conditions…").score(0.9).metadata(Map.of(
                "doc_id", "AM-1", "outgoing_links", List.of(
                        Map.of("process_id", ADHESION, "title", "Adhésion"), Map.of("target_doc_id", "AM-12")))).build();
        var process = LinkedProcessAttacherTest.process("p1", ADHESION, "{\"nodes\":[]}");
        var lookup = mock(BusinessProcessLookup.class);
        when(lookup.find(any())).thenReturn(new DocumentLookup.LookupResult(List.of(process), false, null));

        String nl = System.lineSeparator();
        List<Document> sink = new ArrayList<>();
        String result = search(aideMemoire, new LinkedProcessAttacher(lookup, new LinkedProcessProperties(true, 1, 20_000)), sink);

        assertTrue(result.contains("<linked_sources>" + nl + "- from=a1; collection=documentation; doc_id=AM-12" + nl
                + "</linked_sources>" + nl + "<linked_processes>" + nl), result);
        assertTrue(result.contains("<linked_process from=\"a1\">" + nl + MetadataFormatter.format(process, true, true)), result);
        assertTrue(result.endsWith("</linked_processes>"), result);
        assertEquals(List.of("a1", "p1"), sink.stream().map(Document::getId).toList());

        List<Document> sinkWhenDisabled = new ArrayList<>();
        String disabled = search(aideMemoire, new LinkedProcessAttacher(lookup, new LinkedProcessProperties(false, 1, 20_000)),
                sinkWhenDisabled);
        assertFalse(disabled.contains("<linked_processes>"), disabled);
        assertTrue(disabled.contains("collection=business_process; process_id=" + ADHESION), disabled);
        assertEquals(List.of("a1"), sinkWhenDisabled.stream().map(Document::getId).toList());
    }

    private static String search(Document result, LinkedProcessAttacher attacher, List<Document> sink) {
        var vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(result));
        var reranker = mock(DocumentReranker.class);
        when(reranker.rerank(anyString(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));
        var userService = mock(UserService.class);
        when(userService.hasAccessToInternalDocuments("u1")).thenReturn(true);
        var sourceResolver = mock(SourceResolver.class);
        when(sourceResolver.resolve("AFAC")).thenReturn(List.of("afac"));

        var tool = new RAGTool(mock(ChatModel.class), vectorStore, userService, reranker,
                retrievalWithoutQueryTransformation(), mock(JdbcTemplate.class), sourceResolver,
                mock(WorkspaceService.class), mock(WorkspaceInferenceMonitoringService.class), attacher);

        Map<String, Object> context = new HashMap<>();
        context.put(ToolContextKeys.CTX_USER_ID, "u1");
        context.put(ToolContextKeys.CTX_LANGUAGE, "fr");
        context.put(ToolContextKeys.CTX_WORKSPACE, "AFAC");
        context.put(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, sink);
        return tool.searchSocialInsuranceDocumentation("Quelles sont les conditions d'adhésion ?", new ToolContext(context));
    }

    private static Document chunk(String id, Map<String, Object> metadata) {
        return new Document(id, "contenu", metadata);
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
                retrievalWithoutQueryTransformation(), mock(JdbcTemplate.class), sourceResolver, workspaceService, monitoring,
                new LinkedProcessAttacher(mock(BusinessProcessLookup.class), new LinkedProcessProperties(true, 1, 20_000)));

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
