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

    public record Qa (String question, String answer) {
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
            @ToolParam(description = "Y a-t-il une augmentation du degré d'invalidité") String question1,
            @ToolParam(description = "Y a-t-il un changement de palier selon l'ancien système ? (Oui/Non/unknown)") String question2,
            @ToolParam(description = "S'agit-il d'une révision (sur demande ou d'office) ? (Oui/Non/unknown)") String question3,
            @ToolParam(description = "S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022 ? (Oui/Non/unknown)") String question4,
            @ToolParam(description = "Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ? (Oui/Non/unknown)") String question5,
            @ToolParam(description = "Y a-t-il eu une modification d'au moins 5% du degré d'invalidité ? (Oui/Non/unknown)") String question6,
            @ToolParam(description = "Y a-t-il une augmentation du degré d'invalidité ? (Oui/Non/unknown)") String question7,
            @ToolParam(description = "L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ? (Oui/Non/unknown)") String question8,
            @ToolParam(description = "Le taux d'invalidité est-il d'au-moins 50% ? (Oui/Non/unknown)") String question9,
            @ToolParam(description = "Y a-t-il une diminution du montant de la rente ? (Oui/Non/unknown)") String question10,
            @ToolParam(description = "Le taux d'invalidité est-il d'au-moins 70% ? (Oui/Non/unknown)") String question11,
            @ToolParam(description = "Droit ouvert dans le système linéaire ? (Oui/Non/unknown)") String question12,
            @ToolParam(description = "Y a-t-il une augmentation du montant de la rente ? (Oui/Non/unknown)") String question13
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
                "Y a-t-il eu une augmentation du taux depuis le 01.01.2024?",
                "Y a-t-il un changement de palier selon l'ancien système?",
                "S'agit-il d'une révision (sur demande ou d'office)?",
                "S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?",
                "Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?",
                "Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?",
                "Y a-t-il une augmentation du degré d'invalidité?",
                "L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?",
                "Le taux d'invalidité est-il d'au-moins 50%?",
                "Y a-t-il une diminution du montant de la rente?",
                "Le taux d'invalidité est-il d'au-moins 70%?",
                "Droit ouvert dans le système linéaire?",
                "Y a-t-il une augmentation du montant de la rente?"
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

        var previousQuestionAnswerPairs = holder.getAnsweredQuestions(conversationId);

        previousQuestionAnswerPairs.ifPresentOrElse(pairs -> {
            pairs.forEach(prevQa -> {
                if (prevQa.answer().equals("unknown")) {
                    questionAnswerPairs.stream()
                            .filter(newQa -> newQa.question().equals(prevQa.question()))
                            .findFirst()
                            .ifPresent(newQa -> {
                                // Since Qa is a record and immutable, we need to create a new instance
                                pairs.set(
                                        pairs.indexOf(prevQa),
                                        new Qa(prevQa.question(), newQa.answer())
                                );
                            });
                }
            });
            holder.setAnsweredQuestions(conversationId, pairs);
        }, () -> {
            holder.setAnsweredQuestions(conversationId, questionAnswerPairs);
        });
        holder.setAnsweredQuestions(conversationId, questionAnswerPairs);
        var decision = InvalidityRateSystem.getDecision(holder.getAnsweredQuestions(conversationId).orElse(List.of()));

        return decision;
    }

    @Tool(
            name = "get_invalidity_rate_system2",
            description = """
                    Détermine le système à utilisé pour calculer la rente d'invalidité d'un assuré.
                    Pour cela, il utilise des pairs de questions réponses qu'il compare avec les chemins existants d'un arbre de décision.
                    Les paramètres sont des strings qui contiennent soit la réponse à la question posée correspondant à sa déscription ('Oui'/'Non') si celle-ci est connue,
                    soit, si les informations fournis ne permettent pas d'y répondre, 'unknown'.
                    Afin de définir les valeurs des paramètres, il est nécessaire d'extraire les réponses aux questions ci-dessous, via les informations fournies par l'utilisateur.
                    
                    Liste de questions:
                    - question1: Y a-t-il eu une augmentation du taux depuis le 01.01.2024 ?
                    - question2: Y a-t-il un changement de palier selon l'ancien système ?
                    - question3: S'agit-il d'une révision (sur demande ou d'office) ?
                    - question4: S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022 ?
                    - question5: Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ?'
                    - question6: Y a-t-il eu une modification d'au moins 5% du degré d'invalidité ?
                    - question7: Y a-t-il une augmentation du degré d'invalidité ?
                    - question8: L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ?
                    - question9: Le taux d'invalidité est-il d'au-moins 50% ?
                    - question10: Y a-t-il une diminution du montant de la rente ?erw34c x
                    - question11: Le taux d'invalidité est-il d'au-moins 70% ?
                    - question12: Droit ouvert dans le système linéaire ?
                    - question13: Y a-t-il une augmentation du montant de la rente ?
                    
                    Les informations à traiter peuvent se présenter sous 2 formes différentes:
                    1. l'utilisateur décris de lui-même la situation:
                        exemple 1: USER: Il s'agit d'une révision d'office ouverte dans le système linéaire suite à une modification des faits en novembre 2024.
                            -> question3 = 'Oui', question5 = 'Oui', question12 = 'Oui', les autres questions = 'unknown'
                        exemple 2: USER: Le degré d’invalidité a augmenté d’au-moins 5%.
                            -> question6: 'Oui', question7: 'Oui, les autres questions = 'unknown'
                    2. l'utilisateur répond à une question posée par l'assistant:
                        exemple 3: ASSISTANT: Y a-t-il une diminution du montant de la rente ?
                            USER: Non
                            -> question10 = 'Non', les autres questions = 'unknown'
                        exemple 4: ASSISTANT: Y a-t-il une diminution du montant de la rente ?
                            USER: Non il a augmenté.
                            -> question10 = 'Non', question13: 'Oui, les autres questions = 'unknown'
                    
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
    String getInvalidityRateSystem2(
                    @ToolParam(description = "la réponse à la question fermée 'Y a-t-il eu une augmentation du taux depuis le 01.01.2024 ?'.") String question1,
                    @ToolParam(description = "la réponse à la question fermée 'Y a-t-il un changement de palier selon l'ancien système ?'") String question2,
                    @ToolParam(description = "la réponse à la question fermée 'S'agit-il d'une révision (sur demande ou d'office) ?'.") String question3,
                    @ToolParam(description = "la réponse à la question fermée 'S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022 ?'") String question4,
                    @ToolParam(description = "la réponse à la question fermée 'Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023 ?'.") String question5,
                    @ToolParam(description = "la réponse à la question fermée 'Y a-t-il eu une modification d'au moins 5% du degré d'invalidité ?'.") String question6,
                    @ToolParam(description = "la réponse à la question fermée 'Y a-t-il une augmentation du degré d'invalidité ?'.") String question7,
                    @ToolParam(description = "la réponse à la question fermée 'L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans ?'") String question8,
                    @ToolParam(description = "la réponse à la question fermée 'Le taux d'invalidité est-il d'au-moins 50% ?'") String question9,
                    @ToolParam(description = "la réponse à la question fermée 'Y a-t-il une diminution du montant de la rente ?'") String question10,
                    @ToolParam(description = "la réponse à la question fermée 'Le taux d'invalidité est-il d'au-moins 70% ?'") String question11,
                    @ToolParam(description = "la réponse à la question fermée 'Droit ouvert dans le système linéaire ?'") String question12,
                    @ToolParam(description = "la réponse à la question fermée 'Y a-t-il une augmentation du montant de la rente ?'") String question13
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
                "Y a-t-il eu une augmentation du taux depuis le 01.01.2024?",
                "Y a-t-il un changement de palier selon l'ancien système?",
                "S'agit-il d'une révision (sur demande ou d'office)?",
                "S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?",
                "Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?",
                "Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?",
                "Y a-t-il une augmentation du degré d'invalidité?",
                "L'âge de l'assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?",
                "Le taux d'invalidité est-il d'au-moins 50%?",
                "Y a-t-il une diminution du montant de la rente?",
                "Le taux d'invalidité est-il d'au-moins 70%?",
                "Droit ouvert dans le système linéaire?",
                "Y a-t-il une augmentation du montant de la rente?"
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

        var previousQuestionAnswerPairs = holder.getAnsweredQuestions(conversationId);

        previousQuestionAnswerPairs.ifPresentOrElse(pairs -> {
            pairs.forEach(prevQa -> {
                if (prevQa.answer().equals("unknown")) {
                    questionAnswerPairs.stream()
                            .filter(newQa -> newQa.question().equals(prevQa.question()))
                            .findFirst()
                            .ifPresent(newQa -> {
                                // Since Qa is a record and immutable, we need to create a new instance
                                pairs.set(
                                        pairs.indexOf(prevQa),
                                        new Qa(prevQa.question(), newQa.answer())
                                );
                            });
                }
            });
            holder.setAnsweredQuestions(conversationId, pairs);
        }, () -> {
            holder.setAnsweredQuestions(conversationId, questionAnswerPairs);
        });
        holder.setAnsweredQuestions(conversationId, questionAnswerPairs);
        var params = holder.getAnsweredQuestions(conversationId);
        var decision = InvalidityRateSystem.getDecision(params.orElse(List.of()));

        return decision;
    }


}