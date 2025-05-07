package zas.admin.zec.backend.agent.tools.ai;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.util.ReflectionUtils;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import zas.admin.zec.backend.agent.tools.ai.IncomeCalculation;
import zas.admin.zec.backend.agent.tools.ai.IncomeCalculation.Beneficiary;
import zas.admin.zec.backend.agent.tools.ai.IncomeCalculation.StatisticalSalaryInfo;
import zas.admin.zec.backend.agent.tools.ai.IncomeCalculation.EffectiveSalaryInfo;

import java.lang.reflect.Method;

public class AITools {
    
    @Tool(
           name = "invalidity_rate_calculation",
           description = "Calcul le salaire exigible d'une personne benéficiant d'une rente assurance invalidité. les paramètres sont décris comme ci-dessous:\n" +
                   "    \n" +
                   "Année d'éligibitié: int\n" +
                   "Genre: string\n" +
                   "Salaire effectif avant l'atteinte à la santé:\n" +
                   "    Année: int\n" +
                   "    Salaire: double\n" +
                   "Salaire statistique avant l'atteinte à la santé:\n" +
                   "    Année de réference: 2022 (constante)\n" +
                   "    Branche économique: string\n" +
                   "    Niveau de compétence: int [1:4]\n" +
                   "    Salaire : 0 CHF (constante)\n" +
                   "Salaire effectif après l'atteinte à la santé:\n" +
                   "    Année: int\n" +
                   "    Salaire: double\n" +
                   "Salaire statistique après l'atteinte à la santé:\n" +
                   "    Année de réference: 2022 (constante)\n" +
                   "    Branche économique: string\n" +
                   "    Niveau de compétence: int [1:4]\n" +
                   "    Salaire : 0 CHF (constante)\n" +
                   "Diminution du taux d'activité : int [0:100]\n" +
                   "Réduction: int\n" +
                   "Taux d'activité : int [0:100]"
    )
    String invalidityRateCalculation(
            @ToolParam(description = "Année d'éligibitié.") int yearOfEligibility,
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
            @ToolParam(description = "Autres déduction du taux.") int deduction
            ) {

        var preHealthEffectiveSalaryInfo = new EffectiveSalaryInfo(preHealthEffectiveYear, preHealthEffectiveSalary);
        var postHealthEffectiveSalaryInfo = new EffectiveSalaryInfo(postHealthEffectiveYear, postHealthEffectiveSalary);

        var preHealthStatisticalSalaryInfo = new StatisticalSalaryInfo(2022, 0, preHealthSkillLevel, preHealthEconomicBranch);
        var postHealthStatisticalSalaryInfo = new StatisticalSalaryInfo(2022, 0, postHealthSkillLevel, postHealthEconomicBranch);

        var beneficiary = new Beneficiary(yearOfEligibility, gender, preHealthEffectiveSalaryInfo, preHealthStatisticalSalaryInfo, postHealthEffectiveSalaryInfo, postHealthStatisticalSalaryInfo, activityRate, reduction, deduction);
                
        return IncomeCalculation.getInvalidite(beneficiary);
    }

    // @Tool(
    //        name = "invalidity_rate_calculation",
    //        description = "Calcul le salaire exigible d'une personne benéficiant d'une rente assurance invalidité. les paramètres sont décris comme ci-dessous:\n" +
    //                "    \n" +
    //                "Année d'éligibitié: int\n" +
    //                "Genre: string\n" +
    //                "Salaire effectif avant l'atteinte à la santé:\n" +
    //                "    Année: int\n" +
    //                "    Salaire: double\n" +
    //                "Salaire statistique avant l'atteinte à la santé:\n" +
    //                "    Année de réference: 2022 (constante)\n" +
    //                "    Branche économique: string\n" +
    //                "    Niveau de compétence: int [1:5]\n" +
    //                "    Salaire : 0 CHF (constante)\n" +
    //                "Salaire effectif après l'atteinte à la santé:\n" +
    //                "    Année: int\n" +
    //                "    Salaire: double\n" +
    //                "Salaire statistique après l'atteinte à la santé:\n" +
    //                "    Année de réference: 2022 (constante)\n" +
    //                "    Branche économique: string\n" +
    //                "    Niveau de compétence: int [1:5]\n" +
    //                "    Salaire : 0 CHF (constante)\n" +
    //                "Diminution du taux d'activité : int [0:101]\n" +
    //                "Réduction: int\n" +
    //                "Taux d'activité : int [0:101]"
    // )


}
