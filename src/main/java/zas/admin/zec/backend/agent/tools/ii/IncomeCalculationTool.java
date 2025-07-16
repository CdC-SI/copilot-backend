package zas.admin.zec.backend.agent.tools.ii;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import zas.admin.zec.backend.agent.tools.IIStep;
import zas.admin.zec.backend.agent.tools.ii.model.*;
import zas.admin.zec.backend.agent.tools.ii.service.IncomeCalculationService;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.math.BigDecimal;
import java.time.Year;

/**
 * The IITools class provides utility methods for performing specific calculations
 * and handling operations related to invalidity rate computation for individuals
 * receiving a disability insurance pension. It uses conversation metadata as
 * contextual information during its operations.
 */
@Slf4j
public class IncomeCalculationTool {

    private final ConversationMetaDataHolder holder;
    private final String conversationId;
    private final IncomeCalculationService incomeCalculationService;

    public IncomeCalculationTool(ConversationMetaDataHolder holder, String conversationId, IncomeCalculationService incomeCalculationService) {
        this.holder = holder;
        this.conversationId = conversationId;
        this.incomeCalculationService = incomeCalculationService;
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
                    Taux d'activité : int [0:100]
                    """
    )
    String invalidityRateCalculation(
            @ToolParam(description = "Année d'éligibilité.") int yearOfEligibility,
            @ToolParam(description = "Genre du bénéficiaire. MALE pour homme/masculin, FEMALE pour femme/féminin, RAI pour 26 al. 6 RAI") Gender gender,
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

        Beneficiary beneficiary = new Beneficiary(
                Year.of(yearOfEligibility),
                gender,
                new BeneficiaryDetails(
                        new Salary(Year.of(preHealthEffectiveYear), BigDecimal.valueOf(preHealthEffectiveSalary)),
                        preHealthEconomicBranch,
                        preHealthSkillLevel
                ),
                new BeneficiaryDetails(
                        new Salary(Year.of(postHealthEffectiveYear), BigDecimal.valueOf(postHealthEffectiveSalary)),
                        postHealthEconomicBranch,
                        postHealthSkillLevel
                ),
                activityRate,
                reduction,
                deduction
        );

        var toolSuccess = true;
        var toolResponse = "";
        try {
            var result = incomeCalculationService.disabilityDegree(beneficiary);
            toolResponse = formatToolResponse(result);
            return toolResponse
                    .concat("\nSouhaitez vous que je fasse une proposition de réponse à apporter à l’assuré ?");
        } catch (Exception ex) {
            toolSuccess = false;
            log.error("Error during the tool call invalidity_rate_calculation", ex);
            throw ex;
        } finally {
            if (toolSuccess) {
                holder.setCalculation(conversationId, toolResponse);
                holder.setStep(conversationId, IIStep.EXPLANATION);
            }
        }
    }

    private String formatToolResponse(IIResult result) {
        var loss = result.salaryWithoutDisability().subtract(result.salaryWithDisability());
        var roundedRate = Math.round(result.disabilityRate() * 100.0) / 100.0;
        return """
               Voici les résultats du calcul d'invalidité :
               
               **Revenu sans invalidité :** %.2f CHF
               **Revenu exigible :** %.2f CHF
               **Perte de revenu sur 100%% :** %.2f CHF
               **Taux d’invalidité dans la partie lucrative :** %.2f%%
               **Degré d’invalidité :** %.2f%%
               
               ✅ *Ce calcul est validé et n’est pas généré par un modèle de langage*
               """.formatted(result.salaryWithoutDisability(), result.salaryWithDisability(), loss, result.disabilityRate(), roundedRate);
    }
}