package zas.admin.zec.backend.agent.tools.ii;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation.Beneficiary;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation.EffectiveSalaryInfo;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation.StatisticalSalaryInfo;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class IITools {

    public static class Qa {
        public String question;
        public String answer;

        public Qa(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    private final ConversationMetaDataHolder holder;
    private final String conversationId;

    public IITools(ConversationMetaDataHolder holder, String conversationId) {
        this.holder = holder;
        this.conversationId = conversationId;
    }
    
    @Tool(
           name = "invalidity_rate_calculation",
           returnDirect = true,
           description = """
                   Calcul le salaire exigible d'une personne bénéficiant d'une rente assurance invalidité. les paramètres sont décris comme ci-dessous:
                   
                   Année d'éligibilité: int
                   Genre: string
                   Salaire effectif avant l'atteinte à la santé:
                       Année: int
                       Salaire: double
                   Salaire statistique avant l'atteinte à la santé:
                       Année de réference: 2022 (constante)
                       Branche économique: string
                       Niveau de compétence: int [1:4]
                       Salaire : 0 CHF (constante)
                   Salaire effectif après l'atteinte à la santé:
                       Année: int
                       Salaire: double
                   Salaire statistique après l'atteinte à la santé:
                       Année de réference: 2022 (constante)
                       Branche économique: string
                       Niveau de compétence: int [1:4]
                       Salaire : 0 CHF (constante)
                   Diminution du taux d'activité : int [0:100]
                   Réduction: int
                   Taux d'activité : int [0:100]"""
    )
    String invalidityRateCalculation(
            @ToolParam(description = "Année d'éligibilité.") int yearOfEligibility,
            @ToolParam(description = "Genre du bénéficiaire.") String gender,
            @ToolParam(description = "Salaire effectif avant l'atteinte à la santé.") double preHealthEffectiveSalary,
            @ToolParam(description = "Année du dernier salaire effectif avant l'atteinte à la santé.") int preHealthEffectiveYear,
            @ToolParam(description = "niveau de compétence avant l'atteinte à la santé ([1-4]).") int preHealthSkillLevel,
            @ToolParam(description = "Branche économique avant l'atteinte à la santé.") String preHealthEconomicBranch,
            @ToolParam(description = "Salaire effectif après l'atteinte à la santé.") double postHealthEffectiveSalary,
            @ToolParam(description = "Année du dernier salaire effectif après l'atteinte à la santé.") int postHealthEffectiveYear,
            @ToolParam(description = "niveau de compétence après l'atteinte à la santé ([1-4]).") int postHealthSkillLevel,
            @ToolParam(description = "Branche économique après l'atteinte à la santé.") String postHealthEconomicBranch,
            @ToolParam(description = "Taux d'activité avant l'atteinte à la santé.") int activityRate,
            @ToolParam(description = "Réduction du taux d'activité après l'atteinte à la santé.") int reduction,
            @ToolParam(description = "Autres déduction du taux.") int deduction) {

        var preHealthEffectiveSalaryInfo = new EffectiveSalaryInfo(preHealthEffectiveYear, preHealthEffectiveSalary);
        var postHealthEffectiveSalaryInfo = new EffectiveSalaryInfo(postHealthEffectiveYear, postHealthEffectiveSalary);

        var preHealthStatisticalSalaryInfo = new StatisticalSalaryInfo(2022, 0, preHealthSkillLevel, preHealthEconomicBranch);
        var postHealthStatisticalSalaryInfo = new StatisticalSalaryInfo(2022, 0, postHealthSkillLevel, postHealthEconomicBranch);

        var beneficiary = new Beneficiary(yearOfEligibility, gender, preHealthEffectiveSalaryInfo, preHealthStatisticalSalaryInfo, postHealthEffectiveSalaryInfo, postHealthStatisticalSalaryInfo, activityRate, reduction, deduction);

        var toolSuccess = true;
        try {
            return IncomeCalculation.getInvalidite(beneficiary);
        } catch (Exception ex) {
            toolSuccess = false;
            log.error("Error during the tool call invalidity_rate_calculation", ex);
            throw ex;
        } finally {
            if (toolSuccess) {
                holder.clearMetaData(conversationId);
            }
        }
    }

    @Tool(
           name = "get_invalidity_rate_system",
           description = """
                    Détermine le système à utilisé pour calculer la rente d'invalidité d'un assuré.
                    Pour cela, il utilise des pairs de questions réponses qu'il compare avec les chemins existants d'un arbre de décision.
                    Les paramètres sont des strings qui contiennent soit la réponse à la question posée correspondant à sa déscription (Oui/Non),
                     soit, si les informations fournis ne permettent pas d'y répondre, unknown.
                    l'input au format JSON est le suivant:
                    {
                        [
                            {
                                "question": "Question?",
                                "answer": "Oui"
                            },
                            ...
                            ,{
                                "question": "Question?",
                                "answer": "Non"
                            }
                        ]
                    }
                    """
    )
    String getInvalidityRateSystem(
        @ToolParam(description = "Y a-t-il eu une augmentation du taux depuis le 01.01.2024 ? (Oui/Non/unknown)") String question1,
        @ToolParam(description = "Changement de palier selon l'ancien système ? (Oui/Non/unknown)") String question2,
        @ToolParam(description = "S'agit-il d'une révision sur demande ou d'une révision d'office ? (Oui/Non/unknown)") String question3,
        @ToolParam(description = "S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022 ? (Oui/Non/unknown)") String question4,
        @ToolParam(description = "Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ? (Oui/Non/unknown)") String question5,
        @ToolParam(description = "Le degré d'invalidité s'est-il modifié d'au-moins 5% ? (Oui/Non/unknown)") String question6,
        @ToolParam(description = "Le degré d'invalidité est-il augmenté ? (Oui/Non/unknown)") String question7,
        @ToolParam(description = "L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ? (Oui/Non/unknown)") String question8,
        @ToolParam(description = "Le taux d'invalidité est-il d'au-moins 50% ? (Oui/Non/unknown)") String question9,
        @ToolParam(description = "Le montant de la rente est-il diminué ? (Oui/Non/unknown)") String question10,
        @ToolParam(description = "Le taux d'invalidité est-il d'au-moins 70% ? (Oui/Non/unknown)") String question11,
        @ToolParam(description = "Droit ouvert dans le système linéaire ? (Oui/Non/unknown)") String question12,
        @ToolParam(description = "Le montant de la rente est-il augmenté ? (Oui/Non/unknown)") String question13
    ) {
        List<String> answers = List.of(
            question1,
            question2,
            question3,
            question4,
            question5,
            question6,
            question7,
            question8,
            question9,
            question10,
            question11,
            question12,
            question13
        );
        List<String> questions = List.of(
            "Y a-t-il eu une augmentation du taux depuis le 01.01.2024 ?",
            "Changement de palier selon l'ancien système ?",
            "S'agit-il d'une révision (sur demande ou d'office) ?",
            "S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022 ?",
            "Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ?",
            "Le degré d'invalidité s'est-il modifié d'au-moins 5% ?",
            "Le degré d'invalidité est-il augmenté ?",
            "L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ?",
            "Le taux d'invalidité est-il d'au-moins 50% ?",
            "Le montant de la rente est-il diminué ?",
            "Le taux d'invalidité est-il d'au-moins 70% ?",
            "Droit ouvert dans le système linéaire ?",
            "Le montant de la rente est-il augmenté ?"
        );

        List<Qa> questionAnswerPairs = new ArrayList<>();
        
        for (int i = 0; i < questions.size(); i++) {
            String question = questions.get(i);
            String answer = answers.get(i);
            if (answer.equals("unknown")) {
                continue;
            } else {
                questionAnswerPairs.add(new Qa(question, answer));
            }
        }
        var decision = InvalidityRateSystem.getDecision(questionAnswerPairs);
        return decision;
    }


}