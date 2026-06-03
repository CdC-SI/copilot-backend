package zas.admin.zec.backend.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import zas.admin.zec.backend.actions.api.StreamEvent;
import zas.admin.zec.backend.actions.api.StreamEventType;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.config.properties.RetrievingProperties;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.advisor.RAGAdvisor;
import zas.admin.zec.backend.rag.joiner.RankedDocumentJoiner;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;
import zas.admin.zec.backend.rag.retriever.BM25DocumentRetriever;
import zas.admin.zec.backend.rag.retriever.HybridDocumentRetriever;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.SourceResolver;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static java.util.function.Predicate.not;
import static org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Slf4j
@Service
public class RAGAgent implements Agent {

    private static final String AGENT_NAME = "RAG_AGENT";
    private static final String MODEL_NAME = "zas-internal-model";
    private static final String RSP_ID_PREFIX = "rsp_";
    private static final String META_TITLE = "title";
    private static final String META_STATE = "state";
    private static final String META_URL = "url";
    private static final String META_PAGE_NUM = "page_num";
    private static final String META_SUBSECTION = "subsection";
    private static final String META_SOURCE = "source";
    private static final String META_ORGANIZATIONS = "organizations";
    private static final String META_USER_UUID = "user_uuid";
    private static final String STATE_PERSONAL_UPLOADS = "personal.uploads";
    private static final String ORG_ZAS = "ZAS";
    private static final String EVT_KEY_ID = "id";
    private static final String EVT_KEY_MODEL = "model";
    private static final String EVT_KEY_CREATED_AT = "created_at";
    private static final String EVT_KEY_DELTA = "delta";

    private final ChatClient internalChatClient;
    private final VectorStore documentStore;
    private final UserService userService;
    private final DocumentReranker reranker;
    private final RetrievingProperties retrievingProperties;
    private final AttachmentRepository attachmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SourceResolver sourceResolver;

    public RAGAgent(
            @Qualifier("internalChatModel") ChatModel internalChatModel,
            VectorStore documentStore,
            UserService userService,
            DocumentReranker reranker,
            RetrievingProperties retrievingProperties,
            AttachmentRepository attachmentRepository,
            JdbcTemplate jdbcTemplate, SourceResolver sourceResolver) {

        this.internalChatClient = ChatClient.create(internalChatModel);
        this.documentStore = documentStore;
        this.userService = userService;
        this.reranker = reranker;
        this.retrievingProperties = retrievingProperties;
        this.attachmentRepository = attachmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.sourceResolver = sourceResolver;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentType getType() {
        return AgentType.RAG_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        boolean hasAccessToInternalDocuments = userService.hasAccessToInternalDocuments(userId);
        Advisor rag = getRagAdvisor(question, hasAccessToInternalDocuments, !conversationHistory.isEmpty(), userId);

        return internalChatClient
                .prompt()
                .system(RAGPrompts.getRagSystemPrompt(question.language()).formatted(question.responseFormat()))
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .advisors(rag)
                .user(question.query())
                .stream()
                .chatResponse()
                .flatMap(this::toToken);
    }

    public Flux<StreamEvent> processPublicQuestion(String input) {
        var question = Question.builder().query(input).build().withDefaults();
        Advisor rag = getRagAdvisor(question, false, false, "");

        StreamEvent created = new StreamEvent(
                StreamEventType.CREATED,
                Map.of(
                        EVT_KEY_ID, RSP_ID_PREFIX + UUID.randomUUID(),
                        EVT_KEY_MODEL, MODEL_NAME,
                        EVT_KEY_CREATED_AT, Instant.now().toString()
                )
        );

        Flux<StreamEvent> deltas = internalChatClient
                .prompt()
                .system(RAGPrompts.getRagSystemPrompt(question.language()).formatted(question.responseFormat()))
                .advisors(rag)
                .user(question.query())
                .stream()
                .chatResponse()
                .flatMap(this::toTextToken)
                .filter(not(token -> token.content().isBlank()))
                .map(token -> new StreamEvent(StreamEventType.DELTA, Map.of(EVT_KEY_DELTA, token.content())));

        return Flux.concat(
                Mono.just(created),
                deltas,
                Mono.just(new StreamEvent(StreamEventType.DELTA, Map.of(EVT_KEY_DELTA, "")))
        );
    }

    private Advisor getRagAdvisor(Question question, boolean userHasAccessToInternalDocuments, boolean hasHistory, String userId) {
        var conversationDocuments = getConversationDocuments(question, userId);
        var transformers = transformers(question.language(), hasHistory);
        var queryExpander = expander(question.language(), internalChatClient);
        var filterExpressionSupplier = (java.util.function.Supplier<Filter.Expression>)
                () -> buildExpression(question, userHasAccessToInternalDocuments, userId);

        var vectorRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(documentStore)
                .filterExpression(filterExpressionSupplier)
                .topK(retrievingProperties.topK())
                .build();

        DocumentRetriever documentRetriever = getDocumentRetriever(filterExpressionSupplier, vectorRetriever);

        var documentJoiner = new RankedDocumentJoiner(reranker, conversationDocuments);

        return RAGAdvisor.builder()
                .queryTransformers(transformers)
                .queryExpander(queryExpander)
                .documentRetriever(documentRetriever)
                .documentJoiner(documentJoiner)
                .build();
    }

    private DocumentRetriever getDocumentRetriever(Supplier<Filter.Expression> filterExpressionSupplier, VectorStoreDocumentRetriever vectorRetriever) {
        DocumentRetriever documentRetriever;
        if (retrievingProperties.bm25().enabled()) {
            var bm25Retriever = new BM25DocumentRetriever(
                    jdbcTemplate,
                    retrievingProperties.bm25().topK(),
                    filterExpressionSupplier
            );
            documentRetriever = new HybridDocumentRetriever(vectorRetriever, bm25Retriever);
        } else {
            documentRetriever = vectorRetriever;
        }
        return documentRetriever;
    }

    private List<Document> getConversationDocuments(Question question, String userId) {
        return attachmentRepository.findAllByConversationIdAndUserId(question.conversationId(), userId)
                .stream()
                .map(attachmentEntity -> new Document(
                        attachmentEntity.getContent(),
                        Map.of(
                                META_TITLE, attachmentEntity.getFilename(),
                                META_STATE, STATE_PERSONAL_UPLOADS)
                        )
                )
                .toList();
    }

    private Filter.Expression buildExpression(Question question, boolean userHasAccessToInternalDocuments, String userId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();

        var sources = sourceResolver.resolve(question.workspace());
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

    private List<QueryTransformer> transformers(String language, boolean hasHistory) {
        var transformers = new ArrayList<QueryTransformer>();

        if (retrievingProperties.queryCompresser().enabled() && hasHistory) {
            transformers.add(compresser(language, internalChatClient));
        }
        if (retrievingProperties.queryRewriter().enabled()) {
            transformers.add(rewriter(language, internalChatClient));
        }

        return transformers;
    }

    private QueryTransformer compresser(String lang, ChatClient client) {
        return CompressionQueryTransformer.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate(new PromptTemplate(
                        RAGPrompts.getQueryCompresserTemplate(lang)
                ))
                .build();
    }

    private QueryTransformer rewriter(String lang, ChatClient client) {
        return RewriteQueryTransformer.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate(new PromptTemplate(
                        RAGPrompts.getQueryRewriterTemplate(lang)
                ))
                .build();
    }

    private QueryExpander expander(String lang, ChatClient client) {
        if (!retrievingProperties.queryExpander().enabled()) {
            return (List::of);
        }

        return MultiQueryExpander.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate(new PromptTemplate(
                        RAGPrompts.getQueryExpanderTemplate(lang)
                ))
                .numberOfQueries(retrievingProperties.queryExpander().numberOfExpansions())
                .includeOriginal(retrievingProperties.queryExpander().includeOriginal())
                .build();
    }

    private Flux<Token> toToken(ChatResponse response) {
        return response.hasFinishReasons(Set.of("STOP"))
                ? toSourceToken(response)
                : toTextToken(response);
    }

    private Flux<Token> toTextToken(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            return Flux.just(new TextToken(""));
        }
        return Flux.just(new TextToken(response.getResult().getOutput().getText()));
    }

    private Flux<Token> toSourceToken(ChatResponse response) {
        List<Document> sources = response.getMetadata().get(DOCUMENT_CONTEXT);
        if (sources == null || sources.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(sources)
                .map(doc -> {
                    var meta = doc.getMetadata();
                    if (meta.containsKey(META_URL) && meta.get(META_URL) != "") {
                        return SourceToken.fromURLWithDetails(
                                doc.getId(),
                                (String) meta.get(META_URL),
                                (String) meta.get(META_PAGE_NUM),
                                (String) meta.get(META_SUBSECTION),
                                (String) meta.get(META_STATE)
                        );
                    }
                    return SourceToken.fromFileWithDetails(
                            doc.getId(),
                            (String) meta.get(META_TITLE),
                            (String) meta.get(META_PAGE_NUM),
                            (String) meta.get(META_SUBSECTION),
                            (String) meta.get(META_STATE)
                    );
                });
    }
}
