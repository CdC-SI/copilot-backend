package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
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
import zas.admin.zec.backend.rag.joiner.RankedDocumentJoiner;
import zas.admin.zec.backend.rag.retriever.CopilotDocumentRetriever;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

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
            transformers.add(compresser(chatClient));
        }
        transformers.add(rewriter(chatClient));
        var queryExpander = expander(chatClient);
        var documentRetriever = CopilotDocumentRetriever.builder()
                .documentStore(documentStore)
                .legacyDocumentRetriever(legacyDocRetriever)
                .filterExpression(buildExpression(question, userHasAccessToInternalDocuments))
                .topK(5)
                .build();

        var documentJoiner = userHasAccessToInternalDocuments
                ? new RankedDocumentJoiner(internalChatModel, 5)
                : new RankedDocumentJoiner(publicChatModel, 5);

        var queryAugmenter = augmenter(question.responseFormat());

        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(transformers)
                .queryExpander(queryExpander)
                .documentRetriever(documentRetriever)
                .documentJoiner(documentJoiner)
                .queryAugmenter(queryAugmenter)
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

    private QueryTransformer compresser(ChatClient client) {
        return CompressionQueryTransformer.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate(new PromptTemplate("""
                        Étant donné l'historique de conversation suivant et une question de suivi, votre tâche est de synthétiser
                        une requête concise qui intègre le contexte de l'historique.
                        Assurez-vous que la requête soit claire, spécifique et respecte l'intention de l'utilisateur.
                        Ne fournissez pas d'explications ou de commentaires supplémentaires, retournez uniquement la requête.
        
                        Historique de conversation :
                        {history}
        
                        Question de suivi :
                        {query}
        
                        Requête :
                        """))
                .build();
    }

    private QueryTransformer rewriter(ChatClient client) {
        return RewriteQueryTransformer.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate(new PromptTemplate("""
                        Étant donné une requête utilisateur, réécrivez-la pour obtenir de meilleurs résultats lors de la recherche dans un {target}.
                        Supprimez toute information non pertinente et assurez-vous que la requête soit concise et spécifique.
                        Retournez uniquement la requête réécrite, sans explications ni commentaires supplémentaires.
                        
                        Requête originale :
                        {query}
                        
                        Requête réécrite :
                        """))
                .build();
    }

    private QueryExpander expander(ChatClient client) {
        return MultiQueryExpander.builder()
                .chatClientBuilder(client.mutate())
                .promptTemplate
                        (new PromptTemplate("""
                                Vous êtes un expert en recherche d'informations et en optimisation des recherches.
                                Votre tâche consiste à générer {number} versions différentes de la requête donnée.
                                
                                Chaque variante doit couvrir différentes perspectives ou aspects du sujet,
                                tout en conservant l'intention principale de la requête originale.
                                Les variantes peuvent également couvrir des sous-questions ou des sujets connexes qui pourraient aider à
                                retrouver des informations pertinentes.
                                
                                L'objectif est d'élargir l'espace de recherche et d'améliorer les chances de trouver des informations pertinentes.
                                
                                N'expliquez pas vos choix et n'ajoutez aucun autre texte.
                                Fournissez les {number} variantes de requêtes séparées par des sauts de ligne, sans numérotation ni puces.
                                
                                Requête originale : {query}
                                
                                Variantes de requêtes :
                                """))
                .numberOfQueries(5)
                .build();
    }

    private QueryAugmenter augmenter(String format) {
        return ContextualQueryAugmenter.builder()
                .promptTemplate
                        (new PromptTemplate("""
                                <instructions>
                                    <instruction>Vous êtes le ZAS/EAK-Copilot, un assistant consciencieux et engagé qui fournit des réponses détaillées et précises aux questions du public sur les assurances sociales en Suisse</instruction>
                                    <instruction>Vos réponses se basent exclusivement sur les documents contextuels <doc> dans le <contexte></instruction>
                                    <instruction>Répondez en suivant les consignes dans le <format_de_réponse></instruction>
                                </instructions>
                            
                                <notes_importantes>
                                    <1>Analyse complète : utilisez toutes les informations pertinentes des documents contextuels de manière complète. Procédez systématiquement et vérifiez chaque information afin de vous assurer que tous les aspects essentiels de la question sont entièrement couverts</1>
                                    <2>Précision et exactitude : reproduisez les informations avec exactitude. Soyez particulièrement attentif à ne pas exagérer ou à ne pas utiliser de formulations imprécises. Chaque affirmation doit pouvoir être directement déduite des documents contextuels</2>
                                    <3>Explication et justification : Si la réponse ne peut pas être entièrement déduite des documents contextuels, répondez : « Je suis désolé, je ne peux pas répondre à cette question sur la base des documents à disposition... »</3>
                                    <4>Réponse structurée et claire : formatez votre réponse en Markdown afin d'en améliorer la lisibilité. Utilisez des paragraphes clairement structurés, des listes à puces, des tableaux et, le cas échéant, des liens afin de présenter les informations de manière logique et claire</4>
                                    <5>Chain of Thought (CoT) : procédez étape par étape dans votre réponse. Expliquez le cheminement de votre pensée et comment vous êtes parvenu à votre conclusion en reliant les informations pertinentes du contexte dans un ordre logique</5>
                                    <6>Répondez toujours dans la langue de la question !!!</6>
                                </notes_importantes>
                                
                                <context>
                                    {context}
                                </context>
                                
                                <format_de_réponse>
                                    %s
                                </format_de_réponse>
                                
                                Question: {query}
                                """.formatted(format)))
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
