package zas.admin.zec.backend.agent.advisors;

import org.springframework.ai.chat.client.ChatClient;
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
    private final ChatClient routingClient;
    private final ChatClient convertClient;
    private final ChatModel chatmodel;
    private String etape;

    public IIAdvisor(ConversationMetaDataHolder holder, String conversationId1, ChatModel model) {
        this.routingClient = ChatClient.create(model);
        this.convertClient = ChatClient.create(model);
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
            Pour cela, utilise  <liste_questions/réponses> ci-dessous comme paramètres pour le tool get_invalidity_rate_system.
            <liste_questions/réponses>
                <qa_list>
            </liste_questions/réponses>
            """;

//    get_invalidity_rate_system schema:
//    {
//        "title": "get_invalidity_rate_system",
//            "description": "Détermine le système à utilisé pour calculer la rente d'invalidité d'un assuré. Pour cela, il utilise des pairs de questions réponses qu'il compare avec les chemins existants d'un arbre de décision. Les paramètres sont des strings qui contiennent soit la réponse à la question posée correspondant à sa déscription (Oui/Non), soit, si les informations fournis ne permettent pas d'y répondre, unknown.",
//            "type": "object",
//            "properties": {
//        "question1": {
//            "type": "string",
//                    "description": "Y a-t-il eu une augmentation du taux depuis le 01.01.2024 ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question2": {
//            "type": "string",
//                    "description": "Changement de palier selon l'ancien système ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question3": {
//            "type": "string",
//                    "description": "S'agit-il d'une révision sur demande ou d'une révision d'office ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question4": {
//            "type": "string",
//                    "description": "S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022 ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question5": {
//            "type": "string",
//                    "description": "Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question6": {
//            "type": "string",
//                    "description": "Le degré d'invalidité s'est-il modifié d'au-moins 5% ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question7": {
//            "type": "string",
//                    "description": "Le degré d'invalidité est-il augmenté ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question8": {
//            "type": "string",
//                    "description": "L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question9": {
//            "type": "string",
//                    "description": "Le taux d'invalidité est-il d'au-moins 50% ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question10": {
//            "type": "string",
//                    "description": "Le montant de la rente est-il diminué ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question11": {
//            "type": "string",
//                    "description": "Le taux d'invalidité est-il d'au-moins 70% ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question12": {
//            "type": "string",
//                    "description": "Droit ouvert dans le système linéaire ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        },
//        "question13": {
//            "type": "string",
//                    "description": "Le montant de la rente est-il augmenté ? (Oui/Non/unknown)",
//                    "enum": ["Oui", "Non", "unknown"]
//        }
//    },
//        "required": [
//        "question1",
//                "question2",
//                "question3",
//                "question4",
//                "question5",
//                "question6",
//                "question7",
//                "question8",
//                "question9",
//                "question10",
//                "question11",
//                "question12",
//                "question13"
//                            ]
//    }

    private static final String ETAPE_2 = """
            Ton rôle est d'assister l'utilisateur à déterminer .
            Ce processus est composé de 2 étapes :
            1. Déterminer le système utilisé pour calculer la rente AI.
            2. Calculer le montant de la rente AI.
            Commence par déterminer le système utilisé pour calculer la rente AI:
            1. Interprète les informations fourni par l'utilisateur.
            2. Compare les au pairs question/réponse {qa}.
            """;

    private static final String CONVERT_TO_QA = """
            Ton rôle est d'identifier les questions posées par l'utilisateur et de les convertir en paires question/réponse.
            1. Interprète les informations fourni par l'utilisateur et dans <historique_de_conversation> et identifie celles qui correspondent à des questions comprises dans la liste ci-dessous:
                - Y a-t-il eu une augmentation du taux depuis le 01.01.2024 ?,
                - Changement de palier selon l'ancien sysème?,
                - S'agit-il d'une révision (sur demande ou d'office) ?,
                - S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?,
                - Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ?,
                - Le degré d'invalidité s'est-il modifié d'au-moins 5% ?,
                - Le degré d'invalidité est-il augmenté ?,
                - L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ?,
                - Le taux d'invalidité est-il d'au-moins 50% ?,
                - Le montant de la rente est-il diminué ?,
                - Le taux d'invalidité est-il d'au-moins 70% ?,
                - Droit ouvert dans le sysème linéaire ?,
                - Le montant de la rente est-il augmenté ?
            2. Pour chaque question identifiée, répond par "Oui" ou "Non" selon les informations interpretées.
            3. Si aucune information n'est disponible pour une question, répond par "unknown".
            4. pour chaque pair question/réponse , format la sous la forme suivante: "la question ? la réponse".

            <historique_de_conversation>
                <conversation_history>.
            </historique_de_conversation>
            
            Exemple 1:
             message: "
                        USER: Je dois calculer la rente AI d'un assuré.
                        ASSISTANT: J'ai besoin de plus d'informations pour déterminer le système de rente à utiliser: S'agit-il d'une révision (sur demande ou d'office) ?
                        USER: Oui
                    "
             response:  S'agit-il d'une révision (sur demande ou d'office) ? Oui,
                        ...,
                        Droit ouvert dans le système linéaire ? unknown 

            Exemple 2:
             message: "
                        USER: Je dois calculer la rente AI d'un assuré.
                        ASSISTANT: J'ai besoin de plus d'informations pour déterminer le système de rente à utiliser: S'agit-il d'une révision (sur demande ou d'office) ?
                        USER: Il s'agit d'une révision d'office ouvert dans le système linéaire.
                    "
             response:  Droit ouvert dans le système linéaire ? Oui,
                        S'agit-il d'une révision (sur demande ou d'office) ? Oui,
                        ...,
                        L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ? unknown

            Exemple 3:
             message: "
                        USER: Je dois calculer la rente AI d'un assuré de 45 ans.
                        ASSISTANT: Pour continuer, j'ai besoin de savoir si il s'agit d'une révision (sur demande ou d'office) ou d'une nouvelle demande.
                        USER: Il s'agit d'une nouvelle demande.
                    "
             response:  S'agit-il d'une révision (sur demande ou d'office) ? Non,
                        L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ? Non
                        ...,
                        Droit ouvert dans le système linéaire ? unknown,
             """;

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
            var answeredQuestions = convertPrompt(advisedRequest);
            
            return AdvisedRequest.from(advisedRequest)
                    .systemText(ETAPE_1.replace("<qa_list>", answeredQuestions))
                    .userText(answeredQuestions)
                    .build();

        } else if (etape.equals("2")) {
            return AdvisedRequest.from(advisedRequest)
                    .systemText(ETAPE_2)
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
//        messages.add(new UserMessage(userRequest.userText()));
        StringBuilder history = new StringBuilder();

        if (!messages1.isEmpty()) {
            for (Message message : messages1) {
                history.append(message.getMessageType().getValue()).append(": ").append(message.getText()).append("\n");
            }
        }


        Message userMessage = new UserMessage(userRequest.userText());
//
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(ROUTING_PROMPT.replace("<conversation_history>", history));

        Message systemMessage = systemPromptTemplate.createMessage();

//        messages.add(systemMessage);
//
//        Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
        return chatmodel.call(systemMessage, userMessage);
//        return routingClient
//                .prompt()
//                .messages(userRequest.messages())
//                .user(userRequest.userText())
//                .system(ROUTING_PROMPT)
//                .call()
//                .content();
    }
    
    public String convertPrompt(AdvisedRequest userRequest) {
        List<Message> messages1 = userRequest.messages();
        StringBuilder history = new StringBuilder();

        if (!messages1.isEmpty()) {
            for (Message message : messages1) {
                history.append(message.getMessageType().getValue()).append(": ").append(message.getText()).append("\n");
            }
        }

        Message userMessage = new UserMessage(userRequest.userText());
//
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(CONVERT_TO_QA.replace("<conversation_history>", history));
        Message systemMessage = systemPromptTemplate.createMessage();

        return chatmodel.call(systemMessage, userMessage);
        // Make the LLM call
//        return convertClient
//                .prompt()
//                .messages(userRequest.messages())
//                .system(CONVERT_TO_QA)
//                .user(userRequest.userText())
//                .call()
//                .content();
    }
}
