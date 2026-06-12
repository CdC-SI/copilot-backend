package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.properties.RetrievingProperties;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.joiner.RankedDocumentJoiner;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;
import zas.admin.zec.backend.rag.retriever.BM25DocumentRetriever;
import zas.admin.zec.backend.rag.retriever.HybridDocumentRetriever;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Tool Spring AI exposant la recherche documentaire (RAG) sur la documentation des assurances
 * sociales suisses. Le LLM agentique décide lui-même d'invoquer ce tool via tool-calling.
 *
 * <p>La requête de recherche est fournie par le LLM. Les données contextuelles non fournies par le
 * LLM (identité de l'utilisateur, langue, workspace, conversation) transitent par le
 * {@link ToolContext} ; les clés sont définies dans {@link ToolContextKeys}.</p>
 */
@Slf4j
@Component
public class RAGTool {

    private static final String DEFAULT_LANGUAGE = "fr";

    private static final String META_TITLE = "title";
    private static final String META_STATE = "state";
    private static final String META_SOURCE = "source";
    private static final String META_ORGANIZATIONS = "organizations";
    private static final String META_USER_UUID = "user_uuid";
    private static final String STATE_PERSONAL_UPLOADS = "personal.uploads";
    private static final String ORG_ZAS = "ZAS";
    private static final String NO_DOCS_FOUND = """
            <no_documentation_found>
            The knowledge base search returned NO relevant documents for this query.
            ABSOLUTE RULE: You MUST NOT answer this question — not from memory, not from general knowledge, not by inference.
            Your only permitted response is to inform the user that no relevant documentation was found and that you cannot answer without it.
            </no_documentation_found>
            """;

    private final ChatClient internalChatClient;
    private final VectorStore documentStore;
    private final UserService userService;
    private final DocumentReranker reranker;
    private final RetrievingProperties retrievingProperties;
    private final AttachmentRepository attachmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SourceResolver sourceResolver;

    public RAGTool(
            @Qualifier("internalChatModel") ChatModel internalChatModel,
            VectorStore documentStore,
            UserService userService,
            DocumentReranker reranker,
            RetrievingProperties retrievingProperties,
            AttachmentRepository attachmentRepository,
            JdbcTemplate jdbcTemplate,
            SourceResolver sourceResolver) {

        this.internalChatClient = ChatClient.create(internalChatModel);
        this.documentStore = documentStore;
        this.userService = userService;
        this.reranker = reranker;
        this.retrievingProperties = retrievingProperties;
        this.attachmentRepository = attachmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.sourceResolver = sourceResolver;
    }

    @Tool(name = "search_social_insurance_documentation", description = """
            Recherche dans la documentation officielle des assurances sociales suisses (AVS, AI, APG, PC, \
            allocations familiales, etc.) les passages pertinents pour répondre à une question. \
            À utiliser dès qu'une question porte sur les prestations, cotisations, réglementations, \
            procédures ou tout autre sujet relatif aux assurances sociales suisses. \
            Retourne les extraits de documents pertinents, chacun encadré par des balises <document>.""")
    public String searchSocialInsuranceDocumentation(
            @ToolParam(description = """
                    La requête de recherche, reformulée de façon autonome et explicite à partir de la \
                    question de l'utilisateur et du contexte de la conversation.""")
            String query,
            ToolContext toolContext) {

        Map<String, Object> context = toolContext != null ? toolContext.getContext() : Map.of();
        String userId = asString(context.get(ToolContextKeys.CTX_USER_ID), "");
        String language = asString(context.get(ToolContextKeys.CTX_LANGUAGE), DEFAULT_LANGUAGE);
        String workspace = asString(context.get(ToolContextKeys.CTX_WORKSPACE), "");
        String conversationId = asString(context.get(ToolContextKeys.CTX_CONVERSATION_ID), "");

        // Notifier le frontend que la recherche documentaire est en cours.
        ToolContextKeys.emitStatus(context, ChatStatus.RETRIEVAL, language);

        boolean hasAccessToInternalDocuments = !userId.isBlank()
                && userService.hasAccessToInternalDocuments(userId);

        List<Document> documents = retrieve(query, language, workspace, conversationId, userId, hasAccessToInternalDocuments);

        // Remonter les documents (avec leurs métadonnées) à l'appelant pour reconstruire les sources.
        if (context.get(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS) instanceof Collection<?> sink) {
            @SuppressWarnings("unchecked")
            var documentSink = (Collection<Document>) sink;
            documentSink.addAll(documents);
        }

        log.debug("RAGTool retrieved {} documents for query '{}'", documents.size(), query);
        return formatDocuments(documents);
    }

    private List<Document> retrieve(String query, String language, String workspace,
                                    String conversationId, String userId, boolean hasAccessToInternalDocuments) {

        Supplier<Filter.Expression> filterExpressionSupplier =
                () -> buildExpression(workspace, userId, hasAccessToInternalDocuments);

        var vectorRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(documentStore)
                .filterExpression(filterExpressionSupplier)
                .topK(retrievingProperties.topK())
                .build();

        DocumentRetriever documentRetriever = getDocumentRetriever(filterExpressionSupplier, vectorRetriever);

        // 1. Transform the (already autonomous) query.
        Query transformedQuery = Query.builder().text(query).build();
        for (var queryTransformer : transformers(language)) {
            transformedQuery = queryTransformer.apply(transformedQuery);
        }

        // 2. Expand the query into one or multiple queries.
        List<Query> expandedQueries = expander(language).expand(transformedQuery);

        // 3. Retrieve documents for each query.
        Map<Query, List<List<Document>>> documentsForQuery = new HashMap<>();
        for (Query expandedQuery : expandedQueries) {
            documentsForQuery.put(expandedQuery, List.of(documentRetriever.retrieve(expandedQuery)));
        }

        // 4. Join, rerank and combine with the conversation attachments.
        var conversationDocuments = getConversationDocuments(conversationId, userId);
        var documentJoiner = new RankedDocumentJoiner(reranker, conversationDocuments);
        return documentJoiner.join(documentsForQuery);
    }

    private String formatDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return NO_DOCS_FOUND;
        }

        return documents.stream()
                .map(Document::getText)
                .map(text -> "<document>" + System.lineSeparator() + text + System.lineSeparator() + "</document>")
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private DocumentRetriever getDocumentRetriever(Supplier<Filter.Expression> filterExpressionSupplier,
                                                   VectorStoreDocumentRetriever vectorRetriever) {
        if (retrievingProperties.bm25().enabled()) {
            var bm25Retriever = new BM25DocumentRetriever(
                    jdbcTemplate,
                    retrievingProperties.bm25().topK(),
                    filterExpressionSupplier
            );
            return new HybridDocumentRetriever(vectorRetriever, bm25Retriever);
        }
        return vectorRetriever;
    }

    private List<Document> getConversationDocuments(String conversationId, String userId) {
        if (conversationId.isBlank() || userId.isBlank()) {
            return List.of();
        }
        return attachmentRepository.findAllByConversationIdAndUserId(conversationId, userId)
                .stream()
                .map(attachmentEntity -> new Document(
                        attachmentEntity.getContent(),
                        Map.of(
                                META_TITLE, attachmentEntity.getFilename(),
                                META_STATE, STATE_PERSONAL_UPLOADS)
                ))
                .toList();
    }

    private Filter.Expression buildExpression(String workspace, String userId, boolean userHasAccessToInternalDocuments) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();

        var sources = sourceResolver.resolve(workspace);
        if (!CollectionUtils.isEmpty(sources)) {
            ops.add(builder.in(META_SOURCE, List.copyOf(sources)));
        }

        if (!userHasAccessToInternalDocuments) {
            ops.add(builder.ne(META_ORGANIZATIONS, ORG_ZAS));
        }

        ops.add(builder.group(builder.or(builder.eq(META_USER_UUID, userId), builder.eq(META_USER_UUID, ""))));

        FilterExpressionBuilder.Op combined = ops.getFirst();
        for (int i = 1; i < ops.size(); i++) {
            combined = builder.and(combined, ops.get(i));
        }

        return combined.build();
    }

    private List<QueryTransformer> transformers(String language) {
        var transformers = new ArrayList<QueryTransformer>();
        // Note: la compression basée sur l'historique est désormais à la charge du LLM agentique,
        // qui doit fournir une requête autonome. Seul le réécriture est conservée ici.
        if (retrievingProperties.queryRewriter().enabled()) {
            transformers.add(rewriter(language, internalChatClient));
        }
        return transformers;
    }

    private QueryTransformer rewriter(String lang, ChatClient client) {
        return RewriteQueryTransformer.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate(new PromptTemplate(
                        RAGPrompts.getQueryRewriterTemplate(lang)
                ))
                .build();
    }

    private QueryExpander expander(String lang) {
        if (!retrievingProperties.queryExpander().enabled()) {
            return (List::of);
        }

        return MultiQueryExpander.builder()
                .chatClientBuilder(internalChatClient.mutate())
                .promptTemplate(new PromptTemplate(
                        RAGPrompts.getQueryExpanderTemplate(lang)
                ))
                .numberOfQueries(retrievingProperties.queryExpander().numberOfExpansions())
                .includeOriginal(retrievingProperties.queryExpander().includeOriginal())
                .build();
    }

    private static String asString(Object value, String defaultValue) {
        return value instanceof String s && !s.isBlank() ? s : defaultValue;
    }
}

