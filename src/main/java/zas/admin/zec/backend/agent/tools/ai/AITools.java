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
    // private static final ToolDefinition ircDefinition = ToolDefinition.builder()
    //         .name("invalidityRateCalculation")
    //         .description("Calcule le taux d'invalidité d'un bénéficiaire en fonction des données fournies.")
    //         .inputSchema(
    //                 """
    //                 {
    //                     "type": "object",
    //                     "properties": {
    //                         "abattement": {
    //                             "type": "number",
    //                             "description": "Déduction du taux supplémentaire, applicable uniquement si la date d'exigibilité est inférieur à 2024"
    //                         },
    //                         "diminution": {
    //                             "type": "number",
    //                             "description": "Réduction du taux d'activité après l'atteinte à la santé en %"
    //                         },
    //                         "ess": {
    //                             "type": "number",
    //                             "description": "Correspond à l'année d'exigibilié du cas"
    //                         },
    //                         "ex": {
    //                             "type": "object",
    //                             "properties": {
    //                                 "année": {
    //                                     "type": "number",
    //                                     "description": "L'année de référence, toujours égal à 2022"
    //                                 },
    //                                 "branche": {
    //                                     "type": "string",
    //                                     "description": "La branche économique"
    //                                 },
    //                                 "niveau_comp": {
    //                                     "type": "number",
    //                                     "enum": [1, 2, 3, 4],
    //                                     "description": "Le niveau de compétence"
    //                                 }
    //                             },
    //                             "required": ["année", "branche", "niveau_comp"]
    //                         },
    //                         "horaire": {
    //                             "type": "number",
    //                             "description": "Taux d'activité en %"
    //                         },
    //                         "salaire_as": {
    //                             "type": "object",
    //                             "properties": {
    //                                 "année": {
    //                                     "type": "number",
    //                                     "description": "La dernière année où le salaire a été perçu"
    //                                 },
    //                                 "salaire": {
    //                                     "type": "number",
    //                                     "description": "le revenu annuel"
    //                                 }
    //                             },
    //                             "required": ["année", "salaire"]
    //                         },
    //                         "sainv": {
    //                             "type": "object",
    //                             "properties": {
    //                                 "année": {
    //                                     "type": "number",
    //                                     "description": "L'année de référence, toujours égal à 2022"
    //                                 },
    //                                 "branche": {
    //                                     "type": "string",
    //                                     "description": "La branche économique"
    //                                 },
    //                                 "niveau_comp": {
    //                                     "type": "number",
    //                                     "enum": [1, 2, 3, 4],
    //                                     "description": "Le niveau de compétence"
    //                                 }
    //                             },
    //                             "required": ["année", "branche", "niveau_comp"]
    //                         },
    //                         "salaire_effectif": {
    //                             "type": "object",
    //                             "properties": {
    //                                 "année": {
    //                                     "type": "number",
    //                                     "description": "L'année où le salaire est perçu"
    //                                 },
    //                                 "salaire": {
    //                                     "type": "number",
    //                                     "description": "le revenu annuel"
    //                                 }
    //                             },
    //                             "required": ["année", "salaire"]
    //                         },
    //                         "sexe": {
    //                             "type": "string",
    //                             "enum": ["homme", "femme", "26 al. 6 RAI"],
    //                             "description": "Le sexe du bénéficiaire"
    //                         }
    //                     },
    //                     "required": [
    //                         "abattement",
    //                         "diminution",
    //                         "ess",
    //                         "ex",
    //                         "horaire",
    //                         "salaire_as",
    //                         "sainv",
    //                         "salaire_effectif",
    //                         "sexe"
    //                     ]
    //                 }
    //                 """
    //         )
    //         .build();

    // static final Method invalidityRateCalculation = ReflectionUtils.findMethod(IncomeCalculation.class, "getInvalidite", IncomeCalculation.Beneficiary.class);
    // // System.out.println(invalidityRateCalculation);
    // public static ToolCallback ircCallback = MethodToolCallback.builder()
    //         .toolDefinition(ToolDefinition.builder(invalidityRateCalculation)
    //                 .description("Calcule le taux d'invalidité d'un bénéficiaire en fonction des données fournies.")
    //                 .build())
    //         // .toolDefinition(ircDefinition)
    //         .toolMethod(invalidityRateCalculation)
    //         .toolObject(new IncomeCalculation())
    //         .build();

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
                   "    Niveau de compétence: int [1:5]\n" +
                   "    Salaire : 0 CHF (constante)\n" +
                   "Salaire effectif après l'atteinte à la santé:\n" +
                   "    Année: int\n" +
                   "    Salaire: double\n" +
                   "Salaire statistique après l'atteinte à la santé:\n" +
                   "    Année de réference: 2022 (constante)\n" +
                   "    Branche économique: string\n" +
                   "    Niveau de compétence: int [1:5]\n" +
                   "    Salaire : 0 CHF (constante)\n" +
                   "Diminution du taux d'activité : int [0:101]\n" +
                   "Réduction: int\n" +
                   "Taux d'activité : int [0:101]"
    )
    String invalidityRateCalculation(
            @ToolParam(description = "Année d'éligibitié.") int yearOfEligibility,
            @ToolParam(description = "Genre du bénéficiaire.") String gender,
            @ToolParam(description = "Salaire effectif avant l'atteinte à la santé.") double preHealthEffectiveSalary,
            @ToolParam(description = "Année du dernier salaire effectif avant l'atteinte à la santé.") int preHealthEffectiveYear,
            @ToolParam(description = "niveau de compétence avant l'atteinte à la santé ([1-5]).") int preHealthSkillLevel,
            @ToolParam(description = "Branche économique avant l'atteinte à la santé.") String preHealthEconomicBranch,
            @ToolParam(description = "Salaire effectif après l'atteinte à la santé.") double postHealthEffectiveSalary,
            @ToolParam(description = "Année du dernier salaire effectif après l'atteinte à la santé.") int postHealthEffectiveYear,
            @ToolParam(description = "niveau de compétence après l'atteinte à la santé ([1-5]).") int postHealthSkillLevel,
            @ToolParam(description = "Branche économique après l'atteinte à la santé.") String postHealthEconomicBranch,
            @ToolParam(description = "Taux d'activité avant l'atteinte à la santé.") int activityRate,
            @ToolParam(description = "Réduction du taux d'activité après l'atteinte à la santé.") int reduction,
            @ToolParam(description = "Autres déduction du taux.") int deduction) {

        var preHealthEffectiveSalaryInfo = new EffectiveSalaryInfo(preHealthEffectiveYear, preHealthEffectiveSalary);
        var postHealthEffectiveSalaryInfo = new EffectiveSalaryInfo(postHealthEffectiveYear, postHealthEffectiveSalary);

        var preHealthStatisticalSalaryInfo = new StatisticalSalaryInfo(2022, 0, preHealthSkillLevel, preHealthEconomicBranch);
        var postHealthStatisticalSalaryInfo = new StatisticalSalaryInfo(2022, 0, postHealthSkillLevel, postHealthEconomicBranch);

        var beneficiary = new Beneficiary(yearOfEligibility, gender, preHealthEffectiveSalaryInfo, preHealthStatisticalSalaryInfo, postHealthEffectiveSalaryInfo, postHealthStatisticalSalaryInfo, activityRate, reduction, deduction);
                
        return IncomeCalculation.getInvalidite(beneficiary);
    }




}
