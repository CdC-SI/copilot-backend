package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.joiner.RankedDocumentJoiner;
import zas.admin.zec.backend.rag.retriever.InternalDocumentRetriever;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.util.List;
import java.util.Set;

import static org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Service
public class RAGAgent implements Agent {

    private final ChatModel internalChatModel;
    private final ChatModel publicChatModel;
    private final ChatClient internalChatClient;
    private final ChatClient publicChatClient;
    private final VectorStore internalDocumentStore;
    private final DocumentRetriever publicDocRetriever;
    private final UserService userService;

    public RAGAgent(
            @Qualifier("internalChatModel") ChatModel internalChatModel,
            @Qualifier("publicChatModel") ChatModel publicChatModel,
            VectorStore internalDocumentStore,
            DocumentRetriever publicDocRetriever,
            UserService userService) {

        this.internalChatModel = internalChatModel;
        this.publicChatModel = publicChatModel;
        this.internalChatClient = ChatClient.create(internalChatModel);
        this.publicChatClient = ChatClient.create(publicChatModel);
        this.internalDocumentStore = internalDocumentStore;
        this.publicDocRetriever = publicDocRetriever;
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
        Advisor rag = getRagAdvisor(hasAccessToInternalDocuments);
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

    private Advisor getRagAdvisor(boolean userHasAccessToInternalDocuments) {
        var documentRetriever = userHasAccessToInternalDocuments
                ? InternalDocumentRetriever.builder()
                    .internalDocumentStore(internalDocumentStore)
                    .publicDocumentRetriever(publicDocRetriever)
                    .topK(5)
                    .build()
                : publicDocRetriever;

        var documentJoiner = userHasAccessToInternalDocuments
                ? new RankedDocumentJoiner(internalChatModel, 5)
                : new RankedDocumentJoiner(publicChatModel, 5);

        var queryAugmenter = ContextualQueryAugmenter.builder()
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
                                    <6>Répondez toujours en FRANCAIS !!!</6>
                                </notes_importantes>
                                
                                <context>
                                    {context}
                                </context>
                                
                                <format_de_réponse>
                                    Réponse détaillée
                                </format_de_réponse>
                                
                                Question: {query}
                                """))
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .documentJoiner(documentJoiner)
                .queryAugmenter(queryAugmenter)
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
                    if (doc.getMetadata().containsKey("url")) {
                        return SourceToken.fromURL((String) doc.getMetadata().get("url"));
                    }
                    return SourceToken.fromFile((String) doc.getMetadata().get("title"));
                });
    }
}
