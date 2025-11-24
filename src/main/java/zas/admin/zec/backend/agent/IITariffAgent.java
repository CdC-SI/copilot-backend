package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.actions.visualize.model.MedicalService;
import zas.admin.zec.backend.actions.visualize.VisionService;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.rag.token.StatusToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.TariffService;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IITariffAgent implements Agent {

    private final ChatClient client;
    private final VisionService visionService;
    private final TariffService tariffService;

    public IITariffAgent(@Qualifier("internalChatModel") ChatModel model, VisionService visionService, TariffService tariffService) {
        this.client = ChatClient.create(model);
        this.visionService = visionService;
        this.tariffService = tariffService;
    }

    @Override
    public String getName() {
        return "II_TARIFF_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.II_TARIFF_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        Token ocrStatus = createOcrStatusToken(question);

        Mono<Map<String, List<String>>> tariffsMono = extractTariffsFromOcr(question.attachments());
        Mono<Map<String, List<String>>> tariffsInfosMono = fetchTariffsInfo(tariffsMono);

        Mono<Token> tariffsStatus = createTariffsStatusToken(tariffsMono, question);
        Mono<Token> tariffsInfosStatus = createTariffsInfosStatusToken(tariffsInfosMono, question);

        Mono<String> userInputMono = generateUserInput(tariffsMono, tariffsInfosMono);

        Flux<Token> responseTokens = generateResponseTokens(userInputMono, conversationHistory);

        return Flux.concat(
                Flux.just(ocrStatus),
                tariffsStatus,
                tariffsInfosStatus,
                responseTokens
        );
    }

    private Token createOcrStatusToken(Question question) {
        return new StatusToken(ChatStatus.OCR, question.language());
    }

    private Mono<Map<String, List<String>>> extractTariffsFromOcr(List<MultipartFile> attachments) {
        return Mono.fromCallable(() -> {
                    Map<String, List<String>> result = new HashMap<>();

                    for (int i = 0; i < attachments.size(); i++) {
                        MultipartFile bill = attachments.get(i);
                        String filename = bill.getOriginalFilename() != null ?
                                bill.getOriginalFilename() : "attachment-" + i;

                        List<MedicalService> medicalServices = visionService.extractTariffPositionsFromFile(bill).medicalServices();
                        List<String> tariffCodes = medicalServices.stream()
                                .map(MedicalService::tariffCode)
                                .distinct()
                                .toList();

                        result.put(filename, tariffCodes);
                    }

                    return result;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(15))
                .onErrorReturn(Collections.emptyMap())
                .cache();
    }

    private Mono<Map<String, List<String>>> fetchTariffsInfo(Mono<Map<String, List<String>>> tariffsMono) {
        return tariffsMono
                .flatMap(tariffsMap -> Mono.fromCallable(() -> {
                    Map<String, List<String>> result = new HashMap<>();
                    tariffsMap.forEach((filename, tariffCodes) -> {
                        List<String> infos = tariffCodes.stream()
                                .map(tariffService::getTariffPositionInfos)
                                .toList();
                        result.put(filename, infos);
                    });

                    return result;
                }).subscribeOn(Schedulers.boundedElastic()))
                .timeout(Duration.ofSeconds(15))
                .onErrorReturn(Collections.emptyMap())
                .cache();
    }

    private Mono<Token> createTariffsStatusToken(Mono<Map<String, List<String>>> tariffsMono, Question question) {
        return tariffsMono.map(tariffs -> new StatusToken(ChatStatus.II_TARIFFS, question.language(),
                String.join(", ", tariffs.values().stream().flatMap(List::stream).toList())));
    }

    private Mono<Token> createTariffsInfosStatusToken(Mono<Map<String, List<String>>> tariffsInfosMono, Question question) {
        return tariffsInfosMono.map(infos -> new StatusToken(ChatStatus.II_TARIFFS_ANSWER, question.language()));
    }

    private Mono<String> generateUserInput(Mono<Map<String, List<String>>> tariffsMono, Mono<Map<String, List<String>>> tariffsInfosMono) {
        final String userInputTemplate = """
        Voici les informations relatives aux positions tarifaires extraites du/des devis fournis :
        {tariffsInfos}
        """;

        return Mono.zip(tariffsMono, tariffsInfosMono)
                .map(tuple -> {
                    var infos = tuple.getT2();

                    if (infos.isEmpty()) {
                        return userInputTemplate.replace("{tariffsInfos}", "(Aucune information trouvée)");
                    }

                    StringBuilder tariffsInfosBuilder = new StringBuilder();
                    for (Map.Entry<String, List<String>> entry : infos.entrySet()) {
                        String filename = entry.getKey();
                        List<String> positions = entry.getValue();

                        tariffsInfosBuilder.append("<devis>\n");
                        tariffsInfosBuilder.append("  <name>").append(filename).append("</name>\n");
                        tariffsInfosBuilder.append("  <positions>\n");

                        for (String position : positions) {
                            tariffsInfosBuilder.append("    <position>\n");
                            tariffsInfosBuilder.append("      ").append(position).append("\n");
                            tariffsInfosBuilder.append("    </position>\n");
                        }

                        tariffsInfosBuilder.append("  </positions>\n");
                        tariffsInfosBuilder.append("</devis>\n");
                    }

                    return userInputTemplate.replace("{tariffsInfos}", tariffsInfosBuilder.toString());
                });
    }

    private Flux<Token> generateResponseTokens(Mono<String> userInputMono, List<Message> conversationHistory) {
        final String systemPrompt = """
                <contexte>
                    Analyse de positions tarifaires d'un ou plusieurs devis afin de déterminer si elles sont couvertes par l'Assurance Invalidité (AI) en Suisse.
                </contexte>
                <objectif>
                    Indiquer au gestionnaire quelles positions sont couvertes (prises en charge) par l'AI en Suisse en fournissant une explication pourquoi.
                    Faciliter le travail du gestionnaire en lui fournissant une analyse courte et factuelle sur les positions couvertes par l'AI.
                    Si toutes les positions d’un <devis> sont couvertes par l’AI, indiquer que le devis est entièrement couvert, partiellement couvert ou non couvert.
                </objectif>
                <instructions>
                    Chaque devis est encapsulé dans une balise <devis> avec son nom dans une balise <name> et les positions dans une balise <positions>.
                    - Les positions connues du système sont composées d’un <id>{chapitre} - {sous-chapitre} - {titre} : {code}</id>
                      Ainsi que des <informations> associées de la base de données SSO (Tarif Dentaire).
                    - Les positions inconnues du système sont composées d’un <id>Position : {code}</id>.
                      Ainsi que d’une <note>Position inconnue du système.</note>
                
                    Analyse les <positions> fournies ligne par ligne et pour chaque position, vérifie si:
                    - la balise <informations> est présente (même vide) :
                        -> sauf mention contraire dans les informations, la position est couverte par l'AI: "Couverte"
                        -> les informations précisent explicitement que la position n'est pas couverte par l'AI: "Pas couverte"
                    - la balise <note>Position inconnue</note> est présente :
                        -> il n'existe **PAS** de correspondance : la position n'a pas été retrouvée dans la base de données SSO: "Indisponible"
                
                    Pour chaque position du devis, tu devras fournir ta décision (position Couverte|Pas couverte|Indisponible).
                    Dans le cas d’une position non couverte cite les informations pour justifier ta décision.
                </instructions>
                <format_de_réponse>
                    Dans une première section <h4>Résumé par devis<h4>, fournis un résumé clair et concis pour chaque <devis> analysé.
                    Pour chaque devis un statut global (utilise des icônes si possible):
                    - ✔️ Entièrement couvert: Toutes les <positions> du <devis> sont couvertes par l'AI, **même si d’autres positions associées à d’autres devis ne le sont pas.**
                    - ➖ Partiellement couvert: Certaines positions du <devis> sont couvertes par l'AI, d'autres (du même devis) ne le sont pas ou sont indisponibles.
                    - ✖️ Non couvert: Aucune position du <devis> n'est couverte par l'AI.
                
                    Dans une seconde section <h4>Détail des positions<h4> :
                        - Liste des positions couvertes par l’AI :
                            "{titre court} - {code}"
                            "Statut : **Couverte**""
                        - Liste des positions non couvertes par l’AI :
                            "{titre court} - {code}"
                            "Statut : **Pas couverte**"
                            "Explication : {Explication}"
                          Explication est un petit paragraphe justifiant la non couverture et citant les informations pertinentes issues des données SSO.
                        - Liste des positions indisponibles :
                            "{code}"
                            "Statut : **Indisponible**"
                </format_de_réponse>
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
