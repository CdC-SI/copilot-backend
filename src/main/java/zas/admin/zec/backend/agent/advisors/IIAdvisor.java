package zas.admin.zec.backend.agent.advisors;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.agent.AgentType;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class IIAdvisor implements StreamAroundAdvisor {

    private final ConversationMetaDataHolder holder;
    private final String conversationId;
    private final ChatModel chatmodel;
    private String etape;

    public IIAdvisor(ConversationMetaDataHolder holder, String conversationId1, ChatModel model) {
        this.chatmodel = model;
        this.holder = holder;
        this.conversationId = conversationId1;
    }

    private static final String ROUTING_PROMPT = """
            Ton rôle est de déterminer l'étape du processus de calcul la rente assurance invalidité (AI), d'un assuré, qui doit être exécutée.
            Ce processus est composé de 2 étapes :
             1. Déterminer le système utilisé pour calculer la rente AI.
             2. Calculer le montant de la rente AI.

            Pour déterminer l'étape à exécuter, tu dois analyser les informations dans <historique_de_conversation>:
            <historique_de_conversation>
                <conversation_history>.
            </historique_de_conversation>
            Execute l'étape 1 si tu ne connait pas le système de rente à utiliser compris entre Rente linéaire et Rente par palier.
            Si le système de rente est connu, on exécute l'étape 2.

            Ta réponse ne doit contenir que le numéro de l'étape à exécuter.

            Exemple 1:
             USER: Je dois calculer la rente AI d'un assuré.
             ...
             ASSISTANT: Le système de rente à utiliser est la rente linéaire. J'ai besoin des informations suivantes pour calculer la rente AI: ...
             USER: voici les informations: ...
             -> 2

            Exemple 2:
             USER: Je dois calculer la rente AI d'un assuré.
             -> 1
            Exemple 3:
             USER: Je dois calculer la rente AI d'un assuré.
             ASSISTANT: J'ai besoin de plus d'informations suivantes pour déterminer le système de rente à utiliser: ...
             USER: Voici les informations: ...
             -> 1
            """;

    private static final String ETAPE_1 = """
            Ton rôle est d'assister l'utilisateur à déterminer le système de calcul de rente AI pour un assuré.
            Ton objectif est de récupérer des informations pertinentes et vérifier si elle sont suffisante pour déterminer le système à utiliser.
            - Si il manque des informations pose la question recommandée suivante et demande à l'utilisateur si il a des informations supplémentaires à fournir.
              Dans ta réponse, inclus un résumé des informations pertinentes déjà fournis.
              exemple de réponse: Révision d'office ouverte dans le système linéaire. Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ? Y a-t-il d'autre informations qui vous sembles pertinentes ?
            - Si les informations fournies permettent de déterminer le système à utiliser, formule un résumé des questions/réponses pertinentes pour la décision, puis donne le système à utiliser ainsi que les sources soutenant ce choix.
              exemple de réponse: Pour une révision d'office ouverte dans le système linéaire avec une modification des faits entre le 01.01.2022 et le 31.12.2023, une augmentation d'au-moins 5% du degré d’invalidité ainsi qu'une augmentation du taux depuis le 01.01.2024,
              le système à appliquer est le système linéaire, selon la Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI) et la Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI).
            """;

//    private static final String ETAPE_2 = """
//            Ton rôle est d'assister l'utilisateur à calculer la rente invalidité d'un assuré.
//            Ce processus est composé de 2 étapes :
//            1. Déterminer le système utilisé pour calculer la rente AI.
//            2. Calculer le montant de la rente AI.
//            Commence par déterminer le système utilisé pour calculer la rente AI:
//            1. Interprète les informations fourni par l'utilisateur.
//            2. Compare les au pairs question/réponse {qa}.
//            """;

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        Flux<AdvisedResponse> advisedResponses =
                Mono.just(advisedRequest)
                        .publishOn(Schedulers.boundedElastic())
                        .map(this::before)
                        .flatMapMany(chain::nextAroundStream);

        return advisedResponses.map(ar -> {
            if (onFinishReason().test(ar) && this.etape.equals("2")) {
                ar = after(ar);
            }
            return ar;
        });
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private AdvisedRequest before(AdvisedRequest advisedRequest) {

        // Set the agent in use for future requests
        holder.setCurrentAgentInUse(conversationId, AgentType.II_AGENT);

        this.etape = routing(advisedRequest);

        if (etape.equals("1") || etape.equals("-> 1") || etape.equals("->1")) {
//            var answeredQuestions = convertPrompt(advisedRequest);
            var answeredQuestions = holder.getAnsweredQuestions(conversationId);
            String answer ;
            if (answeredQuestions.isPresent()) {
                answer = """
                        <previous_answered_questions>
                        """;
            }
            return AdvisedRequest.from(advisedRequest)
                    .systemText(ETAPE_1)
                    .build();

        } else if (etape.equals("2")) {
            return AdvisedRequest.from(advisedRequest)
//                    .systemText(ETAPE_2)
                    .build();
        } else {
            throw new IllegalArgumentException("Invalid step: " + etape);
        }
    }

    private AdvisedResponse after(AdvisedResponse advisedResponse) {
        Optional<AgentType> currentAgentInUse = holder.getCurrentAgentInUse(conversationId);
        if (currentAgentInUse.isPresent()) {
            return AdvisedResponse.builder()
                    .adviseContext(advisedResponse.adviseContext())
                    .response(new ChatResponse(List.of(new Generation(
                            new AssistantMessage(""),
                            ChatGenerationMetadata.builder().metadata("suggestion", "ii-salary").build()))))
                    .build();
        }
        return toMarkdown(advisedResponse);
    }

    private Predicate<AdvisedResponse> onFinishReason() {
        return advisedResponse -> advisedResponse.response()
                .getResults()
                .stream()
                .filter(result -> result != null && result.getMetadata() != null
                        && StringUtils.hasText(result.getMetadata().getFinishReason()))
                .findFirst()
                .isPresent();
    }

    private AdvisedResponse toMarkdown(AdvisedResponse advisedResponse) {
        return AdvisedResponse.builder()
                .adviseContext(advisedResponse.adviseContext())
                .response(new ChatResponse(
                        List.of(
                                new Generation(
                                        new AssistantMessage(
                                                stringToMD(advisedResponse.response().getResult().getOutput().getText()))))))
                .build();
    }

    private String stringToMD(String content) {
        return content.replace("\\n", "<br>")
                .replace("\"", "");
    }
    
    public String routing(AdvisedRequest userRequest) {
        List<Message> messages1 = userRequest.messages();
        StringBuilder history = new StringBuilder();

        if (!messages1.isEmpty()) {
            for (Message message : messages1) {
                history.append(message.getMessageType().getValue()).append(": ").append(message.getText()).append("\n");
            }
        }


        Message userMessage = new UserMessage(userRequest.userText());
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(ROUTING_PROMPT.replace("<conversation_history>", history));

        Message systemMessage = systemPromptTemplate.createMessage();

        return chatmodel.call(systemMessage, userMessage);
    }
}
