package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.rag.token.StatusToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class IIAuxPricingAgent implements Agent {

    private final ChatClient client;

    public IIAuxPricingAgent(@Qualifier("publicChatModel") ChatModel model) {
        this.client = ChatClient.create(model);
    }

    @Override
    public String getName() {
        return "II_AUX_PRICING_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.II_AUX_PRICING_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        Token ocrStatus = createOcrStatusToken(question);

        Mono<List<String>> pricesMono = extractPricesFromOcr();
        Mono<List<String>> pricesInfosMono = fetchPricesInfo(pricesMono);

        Mono<Token> pricesStatus = createPricesStatusToken(pricesMono, question);
        Mono<Token> pricesInfosStatus = createPricesInfosStatusToken(pricesInfosMono, question);

        Mono<String> userInputMono = generateUserInput(pricesMono, pricesInfosMono, question);

        Flux<Token> responseTokens = generateResponseTokens(userInputMono, conversationHistory);

        return Flux.concat(
                Flux.just(ocrStatus),
                Flux.merge(pricesStatus, pricesInfosStatus),
                responseTokens
        );
    }

    private Token createOcrStatusToken(Question question) {
        return new StatusToken(ChatStatus.OCR, question.language());
    }

    private Mono<List<String>> extractPricesFromOcr() {
        CompletableFuture<List<String>> pricesCodeExtracted = CompletableFuture.supplyAsync(
                () -> List.of("TP023", "TP25B")
        );
        return Mono.fromFuture(pricesCodeExtracted)
                .timeout(Duration.ofSeconds(15))
                .onErrorReturn(List.of());
    }

    private Mono<List<String>> fetchPricesInfo(Mono<List<String>> pricesMono) {
        CompletableFuture<List<String>> pricesInfos = pricesMono.toFuture().thenApplyAsync(prices -> List.of(
                "TP023: Couvrable - La prestation TP023 est généralement couverte par l'AI selon l'article 15.",
                "TP25B: Non couvrable - La prestation TP25B n'est pas couverte par l'AI selon l'article 20."
        ));
        return Mono.fromFuture(pricesInfos)
                .timeout(Duration.ofSeconds(15))
                .onErrorReturn(List.of());
    }

    private Mono<Token> createPricesStatusToken(Mono<List<String>> pricesMono, Question question) {
        return pricesMono.map(prices -> new StatusToken(ChatStatus.II_PRICES, question.language(), String.join(", ", prices)));
    }

    private Mono<Token> createPricesInfosStatusToken(Mono<List<String>> pricesInfosMono, Question question) {
        return pricesInfosMono.map(infos -> new StatusToken(ChatStatus.II_PRICES_ANSWER, question.language()));
    }

    private Mono<String> generateUserInput(Mono<List<String>> pricesMono, Mono<List<String>> pricesInfosMono, Question question) {
        final String userInputTemplate = """
            Voici les tarifs extraits du document OCRé :
            {prices}
            - 4.000
            - 3.5432
            
            Voici les informations relatives à ces tarifs extraits de la base de données :
            {pricesInfos}
            - 4.000 : AI pas couvert
            
            Question de l'utilisateur : {question}
            """;

        return Mono.zip(pricesMono, pricesInfosMono)
                .map(tuple -> {
                    var prices = tuple.getT1();
                    var infos = tuple.getT2();
                    return userInputTemplate
                            .replace("{prices}", prices.isEmpty() ? "(Aucun tarif trouvé)" : String.join(", ", prices))
                            .replace("{pricesInfos}", infos.isEmpty() ? "(Aucune information trouvée)" : String.join(System.lineSeparator(), infos))
                            .replace("{question}", question.query());
                });
    }

    private Flux<Token> generateResponseTokens(Mono<String> userInputMono, List<Message> conversationHistory) {
        final String systemPrompt = """
                Ton objectif est d’indiquer à l’utilisateur si les prestations demandées sur la base d’un devis
                sont couvrables par l’assurance-invalidité (AI) en Suisse.
                
                La question utilisateur a été enrichie avec les tarifs devisés et les informations relatives à ces
                tarifs si trouvés dans la base de données.
                
                Si des tarifs n’ont pas d’informations associées c’est que ce tarif n’est pas connu.
                
                La réponse attendue pour chaque tarif doit être de l’ordre d’une de ces trois possibilités :
                - "Couvrable" si la prestation est en principe couverte par l’AI
                - "Non couvrable" si la prestation n’est pas couverte par l’AI
                - "Inconnu" si le tarif n’est pas connu
                
                Pour chaque tarif, tu dois fournir une explication concise (1-2 phrases) de la raison de ta
                décision, basée sur les informations relatives à ce tarif. Tu peux citer ces informations.
                """;

        return userInputMono.flatMapMany(userInput -> client
                .prompt()
                .system(systemPrompt)
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .user(userInput)
                .stream()
                .chatResponse()
                .flatMap(this::toTextToken)
        );
    }

    private Flux<Token> toTextToken(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null
            || response.getResult().getOutput().getText() == null) {
            return Flux.just(new TextToken(""));
        }
        return Flux.just(new TextToken(response.getResult().getOutput().getText()));
    }
}
