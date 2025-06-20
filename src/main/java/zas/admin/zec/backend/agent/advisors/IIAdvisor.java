package zas.admin.zec.backend.agent.advisors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.agent.AgentType;
import zas.admin.zec.backend.agent.tools.ii.IITools;
import zas.admin.zec.backend.agent.tools.ii.InvalidityRateSystem;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IIAdvisor implements StreamAroundAdvisor {

    private final ConversationMetaDataHolder holder;
    private final String conversationId;
    private final ChatModel chatmodel;
    private String etape;

    /**
     * Constructs an instance of the IIAdvisor class with the specified conversation metadata holder,
     * conversation ID, and chat model.
     *
     * @param holder the conversation metadata holder used for managing metadata associated with the conversation
     * @param conversationId1 the ID of the conversation for which this advisor is created
     * @param model the chat model used for processing and advising during the conversation
     */
    public IIAdvisor(ConversationMetaDataHolder holder, String conversationId1, ChatModel model) {
        this.chatmodel = model;
        this.holder = holder;
        this.conversationId = conversationId1;
    }

    /**
     * A multi-line string used as a prompt to guide the determination of the step in the
     * disability insurance pension calculation process that needs to be executed.
     */
    public static final String ROUTING_PROMPT = """
            Ton rôle est de déterminer l'étape du processus de calcul la rente assurance invalidité (AI), d'un assuré, qui doit être exécutée.
            Ce processus est composé de 2 étapes :
             1. Déterminer le système utilisé pour calculer la rente AI.
             2. Calculer le montant de la rente AI.

            Pour déterminer l'étape à exécuter, tu dois analyser les informations dans <historique_de_conversation>:
            <historique_de_conversation>
                <conversation_history>.
            </historique_de_conversation>
            Si le système de rente à utiliser ainsi que ses sources ne sont pas mentionné il s'agit probablement de l'étape 1.
            Si l'utilisateur fournis explicitement le système à utiliser alors il s'agit de l'étape 2.

            Ta réponse ne doit contenir que le numéro de l'étape à exécuter.

            Exemple 1:
             USER: Je dois calculer la rente AI d'un assuré.
             ...
             ASSISTANT: Le système à appliquer est le système linéaire, selon la Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI) et la Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI).
                        Souhaitez-vous calculer la rente d'invalidité ?
             USER: oui
             -> 2
            Exemple 2:
             USER: Je dois calculer la rente AI d'un assuré.
             -> 1
            Exemple 3:
             USER: Je dois calculer la rente AI d'un assuré.
             ASSISTANT: J'ai besoin de plus d'informations suivantes pour déterminer le système de rente à utiliser: ...
             USER: Voici les informations: ...
             -> 1
            Exemple 4:
             USER: aide moi à calculer la rente invalidité pour une révision sur demande ouverte dans le système linéaire.
             -> 1
             exemple 5:
             USER: aide moi à calculer la rente invalidité pour une révision sur demande ouverte dans le système linéaire.
             ASSISTANT: J'âi besoin des informations suivantes: Droit ouvert dans le système linéaire ? Y a-t-il un changement de palier selon l'ancien système ?
             USER: le droit est ouvert dans l'ancien système et il n'y a pas eu de changement de palier.
             -> 1
            """;

    /**
     * The constant CONVERT_PROMPT is a structured text prompt designed to guide the conversion
     * of user-provided information into structured data. This prompt defines a specific role and
     * set of instructions for processing a user's input, focusing on extracting answers to
     * predefined questions based on provided information.
     */
    public static final String CONVERT_PROMPT = """
            Ton rôle est de convertir des informations en données structurées.
            ton but est d'extraire les réponses aux questions ci-dessous, via les informations fournies par l'utilisateur.
            Utilise la liste des questions sous forme d'affirmation pour extraire les informations fournis par l'utilisateur.
            Chaque question peut avoir comme réponse 'Oui' ou 'Non'.
            Si aucune information donnée par l'utilisateur ne permet de répondre à une question, ne pas l'inclure dans la réponse.
            
            Liste de questions:
            - question1: Y a-t-il eu une augmentation du taux depuis le 01.01.2024?
            - question2: Y a-t-il un changement de palier selon l'ancien système?
            - question3: S'agit-il d'une révision (sur demande ou d'office)?
            - question4: S'agit-il d'une 1ère demande RER ou demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?
            - question5: Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?'
            - question6: Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?
            - question7: Y a-t-il une augmentation du degré d'invalidité ?
            - question8: L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?
            - question9: Le taux d'invalidité est-il d'au-moins 50%?
            - question10: Y a-t-il une diminution du montant de la rente?
            - question11: Le taux d'invalidité est-il d'au-moins 70%?
            - question12: Droit ouvert dans le système linéaire?
            - question13: Y a-t-il une augmentation du montant de la rente?
    
            Liste des questions sous forme d'affirmation:
            - question1:
                positive: il y a eu une augmentation du taux après le 01.01.2024.
                négative: il n'y a pas eu d'augmentation du taux après le 01.01.2024 / il y a eu une diminution du taux / il n'y a pas eu d'augmentation du taux / le taux n'a pas changé.
            - question2:
                positive: il y a eu un changement de palier.
                négative: il n'y a pas eu de changement de palier / le droit est ouvert dans le nouveau système (linéaire).
            - question3:
                positive: il s'agit d'une révision d'office / il s'agit d'une révision sur demande.
                négative: il s'agit d'une nouvelle demande / il ne s'agit pas d'une révision.
            - question4:
                positive: il s'agit d'une 1ère demande RER dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022 / il s'agit d'une demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022.
                négative: il s'agit d'une 1ère demande RER dépôsée après le 31.06.2021 / il s'agit d'une 1ère demande RER dépôsée avant le 01.07.2021 et échéance délai de carence après le 31.12.2021 / il s'agit d'une demande subséquente dépôsée après le 31.06.2021 / il s'agit d'une demande subséquente dépôsée avant le 01.07.2021 et échéance délai de carence après le 31.12.2021 / il ne s'agit pas d'une nouvelle demande.
            - question5:
                positive: il y a eu une modification des faits  02.01.2022 / il y a eu une modification des faits en février 2022.
                négative: il y a eu une modification des faits le 31.12.2021 / il y a eu une modification des faits le 01.01.2024 / il y a eu une modification des faits en décembre 2021 / il y a eu une modification des faits en janvier 2024 / il n'y a pas eu de modification des faits .
            - question6:
                positive: il y a eu une modification de 5% du degré d'invalidité / il y a eu une augmentation de 6% du degré d'invalidité / il y a eu une diminution de 15% du degré d'invalidité.
                négative: il y a eu une diminution de 4% du degré d'invalidité / il y a eu une augmentation de 2% du degré d'invalidité / il y a eu une modification de 3% du degré d'invalidité / Il n'y a pas eu de changement du degré d'invalidité.
            - question7:
                positive: il y a eu une augmentation de 6% du degré d'invalidité / il y a eu une augmentation du degré d'invalidité
                négative: il y a eu une diminution de 4% du degré d'invalidité / il y a eu une diminution du degré d'invalidité / Il n'y a pas eu de changement du degré d'invalidité.
            - question8:
                positive: l'assuré était agé de 55 ans ou plus en janvier 2022 / l'assuré est né avant le 02.01.1967.
                négative: l'assuré était agé de 54 ans ou moins en janvier 2022 / l'assuré est né après le 01.01.1967 / l'assuré n'était pas agé de 55 ans ou plus en janvier 2022.
            - question9:
                positive: le taux d'invalidité est de 50% / le taux d'invalidité de 70%.
                négative: le taux d'invalidité est de 49% ou inférieur / le taux d'invalidité n'est pas de 50% ou plus.
            - question10:
                positive: il y a eu une diminution du montant de la rente / il y a eu une diminution de 5% du montant de la rente.
                négative: il y a eu une augmentation du montant de la rente / il y a eu une augmentation de 5% du montant de la rente / le montant de la rente n'a pas changé.
            - question11:
                positive: le taux d'invalidité de 70% ou plus.
                négative: Le taux d'invalidité est de 69% ou inférieur / le taux d'invalidité n'est pas de 70% ou plus.
            - question12:
                positive: Le droit est ouvert dans le système linéaire / Il s'agit d'une demande ouverte dans le système linéaire.
                négative: Le droit est ouvert dans le système par palier / Il s'agit d'une demande ouverte dans le système par palier / il y a eu un changement de palier / le droit n'est pas ouvert dans le système linéaire.
            - question13:
                positive: il y a eu une augmentation du montant de la rente / il y a eu une augmentation de 5% du montant de la rente.
                négative: il y a eu une diminution du montant de la rente / il y a eu une diminution de 2% du montant de la rente / le montant de la rente n'a pas changé.

            Réponds uniquement avec un tableau JSON contenant des objets avec les champs "question" et "answer".
            
            Les informations à traiter peuvent se présenter sous 4 formes différentes:
            1. l'utilisateur répond explicitement aux questions en décrivant de lui-même la situation:
                exemple 1: USER: Il s'agit d'une révision d'office ouverte dans le système linéaire suite à une modification des faits en novembre 2024.
                    -> [
                         {"question": "S'agit-il d'une révision (sur demande ou d'office)?", "answer": "Oui"},
                         {"question": "Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?", "answer": "Oui"},
                         {"question": "Droit ouvert dans le système linéaire?", "answer": "Oui"}
                       ]
                exemple 2: USER: Le degré d’invalidité a augmenté d’au-moins 5%.
                    -> [
                         {"question": "Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?", "answer": "Oui"},
                         {"question": "Y a-t-il une augmentation du degré d'invalidité?", "answer": "Oui"}
                       ]
            2. l'utilisateur répond implicitement aux questions en décrivant de lui-même la situation:
                exemple 1: USER: Il s'agit d'une nouvelle demande.
                    -> [
                         {"question": "S'agit-il d'une révision (sur demande ou d'office)?", "answer": "Non"}
                       ]
                exemple 2: USER: Le degré d’invalidité a augmenté d’au-moins 5%.
                    -> [
                         {"question": "Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?", "answer": "Oui"},
                         {"question": "Y a-t-il une augmentation du degré d'invalidité?", "answer": "Oui"}
                       ]
            3. l'utilisateur répond à une question posée par l'assistant:
                exemple 3: ASSISTANT: Y a-t-il une diminution du montant de la rente ?
                    USER: Non
                    -> [
                         {"question": "Y a-t-il une diminution du montant de la rente?", "answer": "Non"}
                       ]
                exemple 4: ASSISTANT: Y a-t-il une diminution du montant de la rente ?
                    USER: Non il a augmenté.
                    -> [
                         {"question": "Y a-t-il une diminution du montant de la rente?", "answer": "Non"},
                         {"question": "Y a-t-il une augmentation du montant de la rente?", "answer": "Oui"}
                       ]
            4. l'utilisateur ne fournie aucune information:
               exemple 5: USER: j'ai besoin de calculer la rente invalidité d'un assuré.
                    -> []
            """;

    /**
     * The ETAPE_1 constant represents the system message used during the first stage of a conversation in the IIAdvisor process.
     */
    private static final String ETAPE_1 = """
            Ton rôle est de reformuler le prompt.
            Si il contient la question recommandée suivante, pose uniquement la question recommandée puis, rappel à l'utilisateur qu'il peut également fournir tout autre information pertinente.
            Si la réponse contient une décision ainsi que ses sources donne le système à utiliser ainsi que les sources soutenant ce choix.
            Dans ta réponse, inclus toujours un résumé des Informations déjà collectée.
            """;

    /**
     * Processes an advised request through a stream advisor chain, managing the interaction logic
     * and dynamically applying pre-processing and post-processing transformations to the request and response.
     * This method ensures specific handling depending on the conversation's current step ("etape").
     *
     * @param advisedRequest the input request containing conversation details, messages, and other metadata
     * @param chain the chain of advisors responsible for orchestrating the flow of advised requests and responses
     * @return a reactive stream (Flux) of advised responses after applying processing rules, such as pre-handling
     *         with {@code before}, streaming through the advisor chain, and post-handling with {@code after}
     */
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        Flux<AdvisedResponse> advisedResponses =
                Mono.just(advisedRequest)
                        .publishOn(Schedulers.boundedElastic())
                        .map(this::before)
                        .flatMapMany(chain::nextAroundStream);

        return advisedResponses.map(ar -> {
            if (onFinishReason().test(ar) && (etape.equals("2") || etape.equals("-> 2") || etape.equals("->2"))) {
                ar = after(ar);
            }
            return ar;
        });
    }

    /**
     * Retrieves the name of the advisor, which is the simple name of the class implementing this method.
     *
     * @return the class's simple name as a {@code String}
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Determines the execution order of this advisor among multiple advisors.
     * A lower value indicates higher priority in execution order.
     *
     * @return an integer representing the order of this advisor
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * Handles the "before" phase of processing an advised request. This method sets the current agent in use
     * for the conversation, determines the current step (etape) of the conversation, and processes the request
     * accordingly based on the step. Steps are handled in the following way:
     * - "1" or related indications ("-> 1", "->1") trigger a prompt conversion and response customization for the system.
     * - "2" or related indications ("-> 2", "->2") continue directly without customization.
     * Any unrecognized step results in an exception.
     *
     * @param advisedRequest the input advised request containing conversation details, messages, and metadata
     * @return a transformed advised request with updates based on the current step of the conversation
     * @throws IllegalArgumentException if the current step (etape) is invalid or unrecognized
     */
    private AdvisedRequest before(AdvisedRequest advisedRequest) {

        holder.setCurrentAgentInUse(conversationId, AgentType.II_AGENT);

        this.etape = holder.getEtape(conversationId)
                .orElseGet(() ->"1");
        // etape = 1 is assigned by default, but it can also be determined by the LLM with the code bellow.
        // this.etape = holder.getEtape(conversationId)
        //          .orElseGet(() ->routing(advisedRequest));
        holder.setEtape(conversationId, etape);

        if (etape.equals("1") || etape.equals("-> 1") || etape.equals("->1")) {

            var answeredQuestions = convertPromptStream(advisedRequest);
            return AdvisedRequest.from(advisedRequest)
                    .systemText(ETAPE_1)
                    .userText(answeredQuestions)
                    .build();

        } else if (etape.equals("2") || etape.equals("-> 2") || etape.equals("->2")) {
            return AdvisedRequest.from(advisedRequest)
                    .build();
        } else {
            throw new IllegalArgumentException("Invalid step: " + etape);
        }
    }

    /**
     * Processes the response after the main conversational logic has been handled.
     * Depending on the current step (`etape`) of the conversation, it may modify the response
     * or return it as is. Specifically, if the step corresponds to "2" or certain variations,
     * it handles the response differently by checking the current agent in use and generating
     * a specific structure. If no modifications are needed, the original response is returned.
     *
     * @param advisedResponse the response object containing information processed by the conversation logic
     * @return an {@code AdvisedResponse} instance, either modified or as originally provided,
     *         depending on the conversation's current step
     */
    private AdvisedResponse after(AdvisedResponse advisedResponse) {

        if (etape.equals("2") || etape.equals("-> 2") || etape.equals("->2")) {
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
        } else {
            return advisedResponse;
        }
    }

    /**
     * Creates a predicate that evaluates whether a given {@code AdvisedResponse} contains at least one result
     * with non-null metadata and a non-empty "finishReason" value. The predicate ensures that the response
     * and its related data are checked for completeness and validity regarding the "finishReason".
     *
     * @return a {@code Predicate<AdvisedResponse>} that returns {@code true} if at least one result in the
     *         provided {@code AdvisedResponse} matches the criteria, otherwise {@code false}
     */
    private Predicate<AdvisedResponse> onFinishReason() {
        return advisedResponse -> {
            assert advisedResponse.response() != null;
            return advisedResponse.response()
                    .getResults()
                    .stream()
                    .anyMatch(result -> result != null && result.getMetadata() != null
                            && StringUtils.hasText(result.getMetadata().getFinishReason()));
        };
    }

    /**
     * Transforms an {@code AdvisedResponse} instance by converting its response text into Markdown format.
     * This method utilizes {@code stringToMD} to convert the response content and rebuilds
     * the {@code AdvisedResponse} with the updated content.
     *
     * @param advisedResponse the input {@code AdvisedResponse} object containing the response text to be transformed
     * @return a new {@code AdvisedResponse} instance with the response text converted to Markdown format
     */
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

    /**
     * Converts a given string into a Markdown-compatible format by replacing certain characters.
     * Specifically, it replaces occurrences of "\n" with a line break ("<br>")
     * and removes double quotes from the string.
     *
     * @param content the input string to be converted
     * @return the transformed string with Markdown-compatible formatting
     */
    private String stringToMD(String content) {
        return content.replace("\\n", "<br>")
                .replace("\"", "");
    }

    /**
     * Processes a user request by constructing a conversation history from the provided messages
     * and generating a system prompt based on the conversation context. The method then utilizes
     * a chat model to produce a response based on the constructed system and user messages.
     *
     * @param userRequest the input request containing user-provided text and a list of messages representing
     *                    the conversation history
     * @return a {@code String} response generated by the chat model based on the processed input
     */
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


    /**
     * Converts the answered questions for a given conversation into a formatted string representation.
     * This method retrieves the list of previously answered questions associated with a specific
     * conversation ID and formats them into a human-readable string. Each question-answer pair
     * is concatenated in the format: "questionanswer", followed by a newline.
     *
     * @param conversationId the unique identifier of the conversation for which the answered questions
     *                        need to be retrieved and formatted
     * @return a string representation of the answered questions for the given conversation ID, or an
     *         empty string if no answered questions are found
     */
    public String answeredQaToString(String conversationId) {
        StringBuilder history = new StringBuilder();
        var params = holder.getAnsweredQuestions(conversationId);

        if (params.isPresent()) {
            history.append("Informations déjà collectée:\n");
            for (IITools.Qa param : params.get()) {
                history.append(param.question())
                        .append(param.answer())
                        .append("\n");
            }
            history.append("\n");
        }
        return history.toString();
    }

    /**
     * Converts a user request into a processed prompt stream, incorporating conversation history
     * and generating a decision based on answered questions. This method interacts with a chat model,
     * processes the user's input, and updates the conversation metadata.
     *
     * @param userRequest the input request containing user-provided text and a list of messages
     *                    representing the conversation context and history
     * @return a string representation combining the formatted answered questions and decision result
     *         based on the conversation process
     */
    public String convertPromptStream(AdvisedRequest userRequest) {
        List<Message> messages = userRequest.messages();
        var usrText = userRequest.userText();

        String history = answeredQaToString(conversationId);

        var lastMessage = (messages.isEmpty()) ? "" : messages.getLast().getText();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(CONVERT_PROMPT),
                new AssistantMessage(history),
                new AssistantMessage(lastMessage)
        ));

        var converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<IITools.Qa>>() {});
        ChatClient chatClient = ChatClient.create(chatmodel);

        Flux<String> flux = chatClient.prompt(prompt)
                .user(u -> u.text("""
                        {userText}
                        {format}
                      """)
                        .param("userText", usrText)
                        .param("format", converter.getFormat()))
                .stream()
                .content();
        String content = String.join("", Objects.requireNonNull(flux.collectList().block()));

        List<IITools.Qa> qas = converter.convert(content);
        holder.setAnsweredQuestions(conversationId, qas);

        var decision = InvalidityRateSystem.getDecision(qas);

        Pattern pattern = Pattern.compile("^Décision : (.+)\nSelon (.+)$");
        Matcher matcher = pattern.matcher(decision);

        if (matcher.matches()) {
            holder.setEtape(conversationId, "2");
        }

        return answeredQaToString(conversationId) + decision;
    }
}
