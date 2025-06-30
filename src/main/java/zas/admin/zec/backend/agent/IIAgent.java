package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.agent.advisors.IIAdvisor;
import zas.admin.zec.backend.agent.tools.ii.IncomeCalculationTool;
import zas.admin.zec.backend.agent.tools.ii.SystemEvaluationTool;
import zas.admin.zec.backend.agent.tools.ii.model.DecisionOutcome;
import zas.admin.zec.backend.agent.tools.ii.model.FollowUpOutcome;
import zas.admin.zec.backend.agent.tools.ii.model.NoDecisionMatchingOutcome;
import zas.admin.zec.backend.agent.tools.ii.model.SystemEvaluation;
import zas.admin.zec.backend.agent.tools.ii.service.IncomeCalculationService;
import zas.admin.zec.backend.agent.tools.ii.service.SystemEvaluationService;
import zas.admin.zec.backend.rag.token.SuggestionToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.util.List;
import java.util.Optional;

@Service
public class IIAgent implements Agent {

    private final ChatClient client;
    private final ConversationMetaDataHolder holder;
    private final SystemEvaluationService evaluationService;
    private final IncomeCalculationService incomeCalculationService;

    public IIAgent(@Qualifier("publicChatModel") ChatModel model,
                   ConversationMetaDataHolder holder,
                   SystemEvaluationService evaluationService,
                   IncomeCalculationService incomeCalculationService) {

        this.client = ChatClient.create(model);
        this.holder = holder;
        this.evaluationService = evaluationService;
        this.incomeCalculationService = incomeCalculationService;
    }

    @Override
    public String getName() {
        return "AI_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.II_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        holder.setCurrentAgentInUse(question.conversationId(), getType());
        Optional<String> step = holder.getStep(question.conversationId());
        if (step.isEmpty() || step.get().equals("decision_tree")) {
            return determineEvaluationSystem(question, conversationHistory);
        } else {
            return calculateIncome(question, conversationHistory);
        }
    }

    private Flux<Token> determineEvaluationSystem(Question question, List<Message> conversationHistory) {
        SystemEvaluation evaluation = client
                .prompt()
                .system("""
                    Tu es un agent d’extraction expert pour l’AVS/AI.
                    Analyse tout l’historique de la conversation et appelle la fonction collectEvaluation en remplissant chaque propriété par true, false ou null.
                    Aucune autre sortie.
                    
                    Important : Si l’historique de la conversation contient déjà l’information du système de rente (Rente linéaire, ou Rente par pallier)
                    d’une décision antérieure, ou d’une précision de l’utilisateur qui souhaite procéder directement au calcul du salaire exigible,
                    alors met la propriété systemAlreadyEvaluated à true sinon à false.
                    
                    Les propriétés à renseigner sont :
                    - systemAlreadyEvaluated: Réponse à la question : Le système de rente à utiliser (Rente linéaire ou Rente par pallier) est-il déjà mentionné dans la conversation ?
                    - rateUpSince2024: Réponse à la question : Y a-t-il eu une augmentation du taux depuis le 01.01.2024 ?
                    - legacyTierChange: Réponse à la question :  Y a-t-il un changement de palier selon l'ancien système ?
                    - revisionCase: Réponse à la question : S'agit-il d'une révision (sur demande ou d'office) ?
                    - rerBeforeJul2021: Réponse à la question : S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022 ?
                    - factsChanged: Réponse à la question : Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ?
                    - invalidityDelta5: Réponse à la question : Y a-t-il eu une modification d'au moins 5% du degré d'invalidité ?
                    - invalidityIncrease: Réponse à la question : Y a-t-il une augmentation du degré d'invalidité ?
                    - age55On2022: Réponse à la question : L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ?
                    - invalidity50Plus: Réponse à la question : Le taux d'invalidité est-il d'au-moins 50% ?
                    - pensionDecrease: Réponse à la question : Y a-t-il une diminution du montant de la rente ?
                    - invalidity70Plus: Réponse à la question : Le taux d'invalidité est-il d'au-moins 70% ?
                    - linearEntitlement: Réponse à la question : Droit ouvert dans le système linéaire ?
                    - pensionIncrease: Réponse à la question : Y a-t-il une augmentation du montant de la rente ?
                    """)
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .user(question.query())
                .tools(new SystemEvaluationTool())
                .call()
                .entity(SystemEvaluation.class);

        if (evaluation != null && evaluation.systemAlreadyEvaluated()) {
            holder.setStep(question.conversationId(), "income_calculation");
            return calculateIncome(question, conversationHistory);
        }

        return evaluate(evaluation, question);
    }

    private Flux<Token> evaluate(SystemEvaluation evaluation, Question question) {
        var outcome = evaluationService.evaluate(evaluation);
        var outcomeString = switch (outcome) {
            case DecisionOutcome(int id, String decision, List<String> rationale, List<String> sources) -> {
                holder.setStep(question.conversationId(), "income_calculation");
                yield """
                    ✅ **Decision :** %s <br>
                    **Cheminement :** <br>
                        %s <br>
                    **Sources :**
                        %s <br><br>
                     Souhaitez-vous de l’aide pour le calcul du montant de la rente ?
                    """.formatted(decision, String.join("<br>", rationale), String.join("<br>", sources));
            }
            case FollowUpOutcome(int id, String questionToAsk) -> """
                    ❓ Question de suivi : %s
                    """.formatted(questionToAsk);
            case NoDecisionMatchingOutcome(List<String> rationale) -> """
                    ❌ Aucune décision ne correspond à l'évaluation.
                    """;
        };

        return Flux.just(new TextToken(outcomeString));
    }

    private Flux<Token> calculateIncome(Question question, List<Message> conversationHistory) {
        return client
                .prompt()
                .system("""
                        Ton objectif est de calculer le salaire exigible pour la rente AI.
                        En appelant la fonction invalidity_rate_calculation, tu dois fournir les informations nécessaires pour le calcul.
                        Si certaines informations ne sont pas fournies dans la conversation indique qu’il manque des informations en redonnant
                        la liste des 13 propriétés à renseigner (avec les valeurs déjà récupérées si présentes).
                        Propriété présente : format : `nom: valeur` (par exemple : `Année d'éligibilité: 2023`).
                        Propriété absente : format : `nom` (par exemple : `Genre`).
                        
                        Exemple de réponse attendue :
                        Pourriez-vous compléter les informations, notamment le genre de l’assuré ainsi que les autres détails nécessaires au calcul ?
                        Voici un rappel des informations requises :
                            1. Année d'éligibilité
                            2. Genre
                            3. Salaire effectif avant l'atteinte à la santé (CHF)
                            4. Année du dernier salaire effectif avant l'atteinte à la santé
                            5. Niveau de compétence avant l'atteinte à la santé (1 à 4)
                            6. Branche économique avant l'atteinte à la santé
                            7. Salaire effectif après l'atteinte à la santé (CHF)
                            8. Année du dernier salaire effectif après l'atteinte à la santé
                            9. Niveau de compétence après l'atteinte à la santé (1 à 4)
                            10. Branche économique après l'atteinte à la santé
                            11. Taux d'activité avant l'atteinte à la santé (0 à 100)
                            12. Réduction du taux d'activité après l'atteinte à la santé (0 à 100)
                            13. Autres déductions (0 à 100)
                        """)
                .messages(conversationHistory.stream().map(this::convertToMessage).toList())
                .user(question.query())
                .tools(new IncomeCalculationTool(holder, question.conversationId(), incomeCalculationService))
                .advisors(new IIAdvisor(holder, question.conversationId()))
                .stream()
                .chatResponse()
                .map(this::convertToToken);
    }

    private Token convertToToken(ChatResponse r) {
        if (r.getResults() == null || r.getResults().isEmpty()) {
            return new TextToken("");
        }
        if (r.getResult().getMetadata().containsKey("suggestion")) {
            return new SuggestionToken(r.getResult().getMetadata().get("suggestion"));
        }
        if (r.getResult().getOutput() == null || r.getResult().getOutput().getText() == null) {
            return new TextToken("");
        }

        return new TextToken(r.getResult().getOutput().getText());
    }
}
