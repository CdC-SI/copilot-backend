package zas.admin.zec.backend.agent.tools.ii;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import zas.admin.zec.backend.agent.tools.ii.IncomeCalculation.Beneficiary;
import zas.admin.zec.backend.agent.tools.ii.IncomeCalculation.EffectiveSalaryInfo;
import zas.admin.zec.backend.agent.tools.ii.IncomeCalculation.StatisticalSalaryInfo;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

@Slf4j
public class IITools {

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
}
