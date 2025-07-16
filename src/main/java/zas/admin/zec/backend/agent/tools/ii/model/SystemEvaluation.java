package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public record SystemEvaluation(
        SystemEvaluationType systemType,
        Boolean rateUpSince2024,
        Boolean legacyTierChange,
        Boolean revisionCase,
        Boolean rerBeforeJul2021,
        Boolean factsChanged,
        Boolean invalidityDelta5,
        Boolean invalidityIncrease,
        Boolean age55On2022,
        Boolean invalidity50Plus,
        Boolean pensionDecrease,
        Boolean invalidity70Plus,
        Boolean linearEntitlement,
        Boolean pensionIncrease) {

    public List<String> questionAnswers() {
        var questions = new HashMap<String, Boolean>();

        questions.put("Y a-t-il eu une augmentation du taux depuis le 01.01.2024?", rateUpSince2024);
        questions.put("Y a-t-il un changement de palier selon l'ancien système?", legacyTierChange);
        questions.put("S'agit-il d'une révision (sur demande ou d'office)?", revisionCase);
        questions.put("S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?", rerBeforeJul2021);
        questions.put("Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?", factsChanged);
        questions.put("Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?", invalidityDelta5);
        questions.put("Y a-t-il une augmentation du degré d'invalidité?", invalidityIncrease);
        questions.put("L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?", age55On2022);
        questions.put("Le taux d'invalidité est-il d'au-moins 50%?", invalidity50Plus);
        questions.put("Y a-t-il une diminution du montant de la rente?", pensionDecrease);
        questions.put("Le taux d'invalidité est-il d'au-moins 70%?", invalidity70Plus);
        questions.put("Droit ouvert dans le système linéaire?", linearEntitlement);
        questions.put("Y a-t-il une augmentation du montant de la rente?", pensionIncrease);

        var qas = new ArrayList<String>();
        questions.forEach((question, answer) -> {
            if (answer != null) {
                qas.add(question + (answer ? " Oui" : " Non"));
            }
        });

        return qas;
    }
}
