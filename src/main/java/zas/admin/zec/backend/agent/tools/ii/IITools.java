package zas.admin.zec.backend.agent.tools.ii;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation.Beneficiary;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation.EffectiveSalaryInfo;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation.StatisticalSalaryInfo;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

/**
 * The IITools class provides utility methods for performing specific calculations
 * and handling operations related to invalidity rate computation for individuals
 * receiving a disability insurance pension. It uses conversation metadata as
 * contextual information during its operations.
 */
@Slf4j
public class IITools {

    /**
     * Represents a question-answer pair with a question and its corresponding answer.
     * Instances of this record are immutable and self-contained.
     *
     * @param question The question as a string.
     * @param answer   The answer corresponding to the question as a string.
     */
    public record Qa(String question, String answer) {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Qa(String question1, String answer1)) {
                return question1.equals(question) && answer1.equals(answer);
            }
            return false;
        }
    }

    private final ConversationMetaDataHolder holder;
    private final String conversationId;

    /**
     * Constructs an instance of IITools.
     *
     * @param holder         Conversation metadata holder used to store and retrieve conversation-specific data.
     * @param conversationId Identifier for the specific conversation instance.
     */
    public IITools(ConversationMetaDataHolder holder, String conversationId) {
        this.holder = holder;
        this.conversationId = conversationId;
    }

    /**
     * Calculates the invalidity rate for a person benefiting from a disability insurance pension.
     * This computation is based on several economic and statistical parameters such as salaries
     * before and after the health event, activity rate, and reductions applicable.
     *
     * @param yearOfEligibility         The eligibility year for disability insurance.
     * @param gender                    The gender of the beneficiary (possible values: "homme", "femme", or "26 al. 6 RAI").
     * @param preHealthEffectiveSalary  The effective salary before the health impact.
     * @param preHealthEffectiveYear    The year of the last effective salary before the health impact.
     * @param preHealthSkillLevel       The skill level before the health impact (range: [1-4]).
     * @param preHealthEconomicBranch   The economic sector before the health impact.
     * @param postHealthEffectiveSalary The effective salary after the health impact.
     * @param postHealthEffectiveYear   The year of the last effective salary after the health impact.
     * @param postHealthSkillLevel      The skill level after the health impact (range: [1-4]).
     * @param postHealthEconomicBranch  The economic sector after the health impact.
     * @param activityRate              The initial activity rate before the health impact.
     * @param reduction                 The reduction of activity rate after the health impact.
     * @param deduction                 The additional deductions to the activity rate.
     * @return A string representing the calculated invalidity rate based on the provided parameters.
     * @throws Exception If an error occurs during the computation process.
     */
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
                    
                    Genre peut prendre les valeurs 'homme', 'femme' et '26 al. 6 RAI'
                    Branche économique peut prendre les valeurs suivantes: 'TOTAL', 'SECTEUR PRIMAIRE ', 'SECTEUR SECONDAIRE', 'Industries extractives', 'Industrie manufacturière',
                     'Industries alimentaires et du tabac', 'Industries du textile et de l’habillement', 'Industries du bois et du papier; imprimerie', 'Cokéfaction,
                     raffinage et industrie chimique', 'Industrie pharmaceutique', 'Industries du caoutchouc et du plastique', 'Fabrication de produits métalliques',
                     'Fabrication de produits électroniques; horlogerie', 'Fabrication d’équipements électriques', 'Fabrication de machines et équipements n.c.a',
                     'Fabrication de matériels de transport', 'Autres industries manufacturières; rép. et inst.', 'Production et distribution d’énergie',
                     'Production et distr. d’eau; gestion des déchets', 'Construction', 'Construction de bâtiments et génie civil', 'Travaux de construction spécialisés',
                     'SECTEUR TERTIAIRE', 'Commerce; réparation d'automobiles et de motocycles', 'Commerce et rép. d’automobiles et de motocycles', 'Commerce de gros', 'Commerce de détail',
                     'Transport et entreposage', 'Transports terrestres et transport par conduites', 'Transports par eau, transports aériens', 'Entreposage et services auxiliaires des transports',
                     'Activités de poste et de courrier', 'Hébergement et restauration', 'Hébergement', 'Restauration', 'Information et communication', 'Édition, audiovisuel et diffusion', 'Télécommunications',
                     'Activités informatiques et services d’information', 'Activités financières et d'assurance', 'Activités des services financiers', 'Assurance', 'Activités aux. de services financiers et d’assurance',
                     'Activités immobilières', 'Activités spécialisées, scientifiques et techniques', 'Activités juridiques et comptables', 'Activités des sièges sociaux; conseil de gestion',
                     'Activités d’architecture et d’ingénierie', 'Recherche-développement scientifique', 'Autres activités spécialisées, scient. et techn.', 'Activités de services administratifs et de soutien',
                     'Activités de services administratifs (sans 78)', 'Activités liées à l'emploi', 'Administration publique', 'Enseignement', 'Santé humaine et action sociale', 'Activités pour la santé humaine',
                     'Hébergement médico-social et social', 'Action sociale sans hébergement', 'Arts, spectacles et activités récréatives', 'Autres activités de services'.
                    """
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