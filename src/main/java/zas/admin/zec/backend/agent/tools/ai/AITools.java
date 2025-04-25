package zas.admin.zec.backend.agent.tools.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

public class AITools {
    private static final ToolDefinition ircDefinition = ToolDefinition.builder()
            .name("invalidityRateCalculation")
            .description("Calcule le taux d'invalidité d'un bénéficiaire en fonction des données fournies.")
            .inputSchema(
                    """
                    {
                        "type": "object",
                        "properties": {
                            "abattement": {
                                "type": "number",
                                "description": "Déduction du taux supplémentaire, applicable uniquement si la date d'exigibilité est inférieur à 2024"
                            },
                            "diminution": {
                                "type": "number",
                                "description": "Réduction du taux d'activité après l'atteinte à la santé en %"
                            },
                            "ess": {
                                "type": "number",
                                "description": "Correspond à l'année d'exigibilié du cas"
                            },
                            "ex": {
                                "type": "object",
                                "properties": {
                                    "année": {
                                        "type": "number",
                                        "description": "L'année de référence, toujours égal à 2022"
                                    },
                                    "branche": {
                                        "type": "string",
                                        "description": "La branche économique"
                                    },
                                    "niveau_comp": {
                                        "type": "number",
                                        "enum": [1, 2, 3, 4],
                                        "description": "Le niveau de compétence"
                                    }
                                },
                                "required": ["année", "branche", "niveau_comp"]
                            },
                            "horaire": {
                                "type": "number",
                                "description": "Taux d'activité en %"
                            },
                            "salaire_as": {
                                "type": "object",
                                "properties": {
                                    "année": {
                                        "type": "number",
                                        "description": "La dernière année où le salaire a été perçu"
                                    },
                                    "salaire": {
                                        "type": "number",
                                        "description": "le revenu annuel"
                                    }
                                },
                                "required": ["année", "salaire"]
                            },
                            "sainv": {
                                "type": "object",
                                "properties": {
                                    "année": {
                                        "type": "number",
                                        "description": "L'année de référence, toujours égal à 2022"
                                    },
                                    "branche": {
                                        "type": "string",
                                        "description": "La branche économique"
                                    },
                                    "niveau_comp": {
                                        "type": "number",
                                        "enum": [1, 2, 3, 4],
                                        "description": "Le niveau de compétence"
                                    }
                                },
                                "required": ["année", "branche", "niveau_comp"]
                            },
                            "salaire_effectif": {
                                "type": "object",
                                "properties": {
                                    "année": {
                                        "type": "number",
                                        "description": "L'année où le salaire est perçu"
                                    },
                                    "salaire": {
                                        "type": "number",
                                        "description": "le revenu annuel"
                                    }
                                },
                                "required": ["année", "salaire"]
                            },
                            "sexe": {
                                "type": "string",
                                "enum": ["homme", "femme", "26 al. 6 RAI"],
                                "description": "Le sexe du bénéficiaire"
                            }
                        },
                        "required": [
                            "abattement",
                            "diminution",
                            "ess",
                            "ex",
                            "horaire",
                            "salaire_as",
                            "sainv",
                            "salaire_effectif",
                            "sexe"
                        ]
                    }
                    """
            )
            .build();

    private static final Method invalidityRateCalculation = ReflectionUtils.findMethod(IncomeCalculation.class, "getInvalidite");
    public static ToolCallback ircCallback = MethodToolCallback.builder()
            .toolDefinition(ircDefinition)
            .toolMethod(invalidityRateCalculation)
            .build();
//    @Tool(
//            name = "invalidity_rate_calculation",
//            description = "Calcul le salaire exigible d'une personne benéficiant d'une rente assurance invalidité. les informations sont fourni selon le format suivant:\n" +
//                    "    \n" +
//                    "    Information personnelle:\n" +
//                    "            Sexe: ____\n" +
//                    "        Information avant l'atteinte à la santé:\n" +
//                    "            année de réference: 2022\n" +
//                    "            Branche: ____\n" +
//                    "            niveau de compétence: __\n" +
//                    "            Taux d'activité : ___%\n" +
//                    "            Revenu sans invalidité effectif ____ : CHF ________\n" +
//                    "        Information après l'atteinte à la santé:\n" +
//                    "            année de réference: 2022\n" +
//                    "            Branche: ______\n" +
//                    "            niveau de compétence: __\n" +
//                    "            Diminution du taux d'activité : __%\n" +
//                    "            Revenu effectif ____: CHF ______\n" +
//                    "            abattement: __%\n" +
//                    "        Date de l'exigibilité : __.__.____\n" +
//                    "\n" +
//                    "    les _ sont remplacé par les informations."
//    )
//    String invalidityRateCalculation(
//            @ToolParam(description = "An object representing the beneficiary's data.") IncomeCalculation.Beneficiary beneficiary
//            ) {
//        return IncomeCalculation.getInvalidite(beneficiary);
//    }




}
