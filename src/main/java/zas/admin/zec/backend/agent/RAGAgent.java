package zas.admin.zec.backend.agent;

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
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.RAGPrompts;
import zas.admin.zec.backend.rag.advisor.RAGAdvisor;
import zas.admin.zec.backend.rag.joiner.RankedDocumentJoiner;
import zas.admin.zec.backend.rag.retriever.CopilotDocumentRetriever;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Service
public class RAGAgent implements Agent {

    private final ChatModel internalChatModel;
    private final ChatModel publicChatModel;
    private final ChatClient internalChatClient;
    private final ChatClient publicChatClient;
    private final VectorStore documentStore;
    private final DocumentRetriever legacyDocRetriever;
    private final UserService userService;

    public RAGAgent(
            @Qualifier("internalChatModel") ChatModel internalChatModel,
            @Qualifier("publicChatModel") ChatModel publicChatModel,
            VectorStore documentStore,
            DocumentRetriever legacyDocRetriever,
            UserService userService) {

        this.internalChatModel = internalChatModel;
        this.publicChatModel = publicChatModel;
        this.internalChatClient = ChatClient.create(internalChatModel);
        this.publicChatClient = ChatClient.create(publicChatModel);
        this.documentStore = documentStore;
        this.legacyDocRetriever = legacyDocRetriever;
        this.userService = userService;
    }

    @Override
    public String getName() {
        return "RAG_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.RAG_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        boolean hasAccessToInternalDocuments = userService.hasAccessToInternalDocuments(userId);
        Advisor rag = getRagAdvisor(question, hasAccessToInternalDocuments, !conversationHistory.isEmpty());
        ChatClient client = hasAccessToInternalDocuments
                ? internalChatClient
                : publicChatClient;

        return client
                .prompt()
                .system(RAGPrompts.getRagSystemPrompt(question.language()).formatted(question.responseFormat()))
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .advisors(rag)
                .user(question.query())
                .stream()
                .chatResponse()
                .flatMap(this::toToken);
    }

    private Advisor getRagAdvisor(Question question, boolean userHasAccessToInternalDocuments, boolean hasHistory) {
        var chatClient = userHasAccessToInternalDocuments
                ? internalChatClient
                : publicChatClient;

        var transformers = new ArrayList<QueryTransformer>();
        if (hasHistory) {
            transformers.add(compresser(question.language(), chatClient));
        }
        transformers.add(rewriter(question.language(), chatClient));
        var queryExpander = expander(question.language(), chatClient);
        var documentRetriever = CopilotDocumentRetriever.builder()
                .documentStore(documentStore)
                .legacyDocumentRetriever(legacyDocRetriever)
                .filterExpression(buildExpression(question, userHasAccessToInternalDocuments))
                .topK(5)
                .build();

        var documentJoiner = userHasAccessToInternalDocuments
                ? new RankedDocumentJoiner(internalChatModel, 5)
                : new RankedDocumentJoiner(publicChatModel, 5);

        return RAGAdvisor.builder()
                .queryTransformers(transformers)
                .queryExpander(queryExpander)
                .documentRetriever(documentRetriever)
                .documentJoiner(documentJoiner)
                .build();
    }

    private Filter.Expression buildExpression(Question question, boolean userHasAccessToInternalDocuments) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();
        if (question.tags() != null && !question.tags().isEmpty()) {
            ops.add(builder.in("tags", List.copyOf(question.tags())));
        }
        if (question.sources() != null && !question.sources().isEmpty()) {
            ops.add(builder.in("source", List.copyOf(question.sources())));
        }
        if (!userHasAccessToInternalDocuments) {
           ops.add(builder.ne("organizations", "ZAS"));
        }

        if (ops.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder.Op combined = ops.getFirst();
        for (int i = 1; i < ops.size(); i++) {
            combined = builder.and(combined, ops.get(i));
        }

        return combined.build();
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
        return MultiQueryExpander.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate(new PromptTemplate(
                        RAGPrompts.getQueryExpanderTemplate(lang)
                ))
                .numberOfQueries(5)
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
                    if (meta.containsKey("url") && meta.get("url") != "") {
                        return SourceToken.fromURLWithDetails(
                                (String) meta.get("url"),
                                (String) meta.get("page_num"),
                                (String) meta.get("subsection"),
                                (String) meta.get("state")
                        );
                    }
                    return SourceToken.fromFileWithDetails(
                            (String) meta.get("title"),
                            (String) meta.get("page_num"),
                            (String) meta.get("subsection"),
                            (String) meta.get("state")
                    );
                });
    }
}
