package zas.admin.zec.backend.agent.tools.ai;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static zas.admin.zec.backend.agent.tools.ai.StatisticalData.*;

public class IncomeCalculation {
    @Getter
    private static StringBuilder fullContext = new StringBuilder();

    private  static void updateContext(String context) {
        fullContext.append(context);
    }

    public static class Beneficiary {
        int yearOfEligibility;
        String gender;
        EffectiveSalaryInfo preHealthEffectiveSalary;
        StatisticalSalaryInfo preHealthStatisticalSalary;
        EffectiveSalaryInfo postHealthEffectiveSalary;
        StatisticalSalaryInfo postHealthStatisticalSalary;
        int activityRate; // in percentage
        int reduction; // % reduction after health impairment
        int deduction; // additional deductions

        public Beneficiary(int yearOfEligibility, String gender, EffectiveSalaryInfo preHealthEffectiveSalary,
                           StatisticalSalaryInfo preHealthStatisticalSalary, EffectiveSalaryInfo postHealthEffectiveSalary,
                           StatisticalSalaryInfo postHealthStatisticalSalary, int activityRate, int reduction,
                           int deduction) {
            this.yearOfEligibility = yearOfEligibility;
            this.gender = gender;
            this.preHealthEffectiveSalary = preHealthEffectiveSalary;
            this.preHealthStatisticalSalary = preHealthStatisticalSalary;
            this.postHealthEffectiveSalary = postHealthEffectiveSalary;
            this.postHealthStatisticalSalary = postHealthStatisticalSalary;

            this.activityRate = activityRate;
            this.reduction = reduction;
            this.deduction = deduction;
        }

        /**
         * Récupère le salaire dans le tableau TA1 pour le genre, la branche et le niveau de compétence donnés.
         */
        public void assignSalaireTa1() {
            // 1) Build the TA1 index for the RAI ids:
            List<Map<String, Object>> ta1Idx = loadTahIndex("ta");

            // 2) Load the TA1 table:
            List<DataPoint> ta1Table = loadTa1();

            // 3) Update each StatisticalSalaryInfo
            for (StatisticalSalaryInfo statInfo : new StatisticalSalaryInfo[] {
                    this.preHealthStatisticalSalary,
                    this.postHealthStatisticalSalary
            }) {
                // 3a) Find the “final” branch id
                String idFinal = getId(statInfo.economicBranch, ta1Idx);

                // 3b) Build the column name (e.g. "1 homme")
                String column = statInfo.skillLevel + " " + this.gender;

                // 3c) Scan the TA1 table for that id:
                boolean found = false;
                for (DataPoint dp : ta1Table) {
                    if (dp.getId().equals(idFinal)) {
                        Double valeur = dp.getIndexValues().get(column);
                        if (valeur == null) {
                            throw new IllegalArgumentException(
                                    "Colonne introuvable '" + column +
                                            "' dans TA1 pour l'id " + idFinal
                            );
                        }
                        statInfo.setSalary(valeur);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new IllegalArgumentException(
                            "Aucune ligne TA1 trouvée pour l'id " + idFinal
                    );
                }
            }
        }

        public void assignId() {
            // loadLabelsId() renvoie Map<id, label>
            Map<String,String> idToLabel = loadLabelsId();
            for (StatisticalSalaryInfo statInfo : new StatisticalSalaryInfo[] {
                    this.preHealthStatisticalSalary,
                    this.postHealthStatisticalSalary
            }) {
                // on cherche la 1ère entrée dont la valeur (label) correspond à 'branche'
                for (Map.Entry<String, String> entry : idToLabel.entrySet()) {
                    if (entry.getValue().equals(statInfo.economicBranch)) {
                        statInfo.setEconomicBranch(entry.getKey());
                    }
                }
            }
        }
    }

    public static class StatisticalSalaryInfo {
        int year;
        @Setter
        double salary;
        int skillLevel;
        @Setter
        String economicBranch;

        public StatisticalSalaryInfo(int year, double salary, int skillLevel, String economicBranch) {
            this.year = year;
            this.salary = salary;
            this.skillLevel = skillLevel;
            this.economicBranch = economicBranch;
        }

    }

    public static class EffectiveSalaryInfo {
        int year;
        double salary;

        public EffectiveSalaryInfo(int year, double salary) {
            this.year = year;
            this.salary = salary;
        }
    }

    public static String getId(String id, List<Map<String,Object>> index) {
        String totalId = index.getFirst().get("id").toString();
        id = (Objects.equals(id, "01-96") || Objects.equals(id, "05-96") ) ? totalId : id;
        List<Map<String,Object>> matches = new ArrayList<>();

        for (Map<String,Object> entry : index) {
            String entryId = (String) entry.get("id");
            List<Integer> entryRange = (List<Integer>) entry.get("index");

            if (id.equals(entryId)) {
                // exact match
                matches.add(entry);
            } else {
                // check if all components of toRange(id) are contained in entryRange
                boolean containsAll = true;
                for (Integer r : toRange(id)) {
                    if (!entryRange.contains(r)) {
                        containsAll = false;
                        break;
                    }
                }
                if (containsAll) {
                    matches.add(entry);
                }
            }
        }

        if (matches.isEmpty()) {
            System.out.println("id not in index");
            return null;
        } else if (matches.size() > 1) {
            // pick the one with the smallest 'index' list
            Map<String,Object> best = matches.getFirst();
            List<Integer> bestRange = (List<Integer>) best.get("index");
            for (Map<String,Object> cand : matches) {
                List<Integer> candRange = (List<Integer>) cand.get("index");
                if (bestRange.size() > candRange.size()) {
                    best = cand;
                    bestRange = candRange;
                }
            }
            return (String) best.get("id");
        } else {
            // exactly one match
            String found = (String) matches.getFirst().get("id");
            System.out.println(found);
            return found;
        }
    }

    /**
     * Récupère l'indice (p. ex. du tableau ESS) pour la branche et l'année spécifiées,
     * en tenant compte du sexe du bénéficiaire.
     */
    public static double getIndexFromT1(Beneficiary benef, int year, String branche) {
        // 1) Charger la table t1 (map sexe -> liste de DataPoint)
        Map<String, List<DataPoint>> t1Map = loadT1();
        List<DataPoint> df = t1Map.get(benef.gender);
        if (df == null) {
            throw new IllegalArgumentException("Sexe inconnu: " + benef.gender);
        }

        // 2) Charger l'index t1 (map sexe -> liste d'objets { id, index })
        Map<String, List<Map<String, Object>>> t1IndexMap = loadT1Index();
        List<Map<String, Object>> indexList = t1IndexMap.get(benef.gender);
        if (indexList == null) {
            throw new IllegalArgumentException("Sexe inconnu pour l'index: " + benef.gender);
        }

        // 3) Résoudre la branche finale (getId gère les plages, / et +)
        String brancheFinale = getId(branche, indexList);

        // 4) Chercher la DataPoint dont l'id correspond
        DataPoint row = df.stream()
                .filter(dp -> dp.getId().equals(brancheFinale))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Branche introuvable dans t1: " + brancheFinale));

        // 5) Récupérer la valeur pour l'année demandée
        String yearKey = String.valueOf(year);
        Double value = row.getIndexValues().get(yearKey);
        if (value == null) {
            throw new IllegalArgumentException("Année " + year + " non trouvée pour la branche " + brancheFinale);
        }

        return value;
    }

    /**
     * Retourne la durée hebdomadaire (ex. 42h) selon la table th,
     * en fonction de la 'branche' du bénéficiaire et de l'année.
     */
    public static double getHeuresHebdo(String branche, int year) {
        // 1) Charger l'index de th (liste de maps { "id": ..., "index": [...] })
        List<Map<String, Object>> thIndex = loadTahIndex("th");

        // 2) Résoudre la branche finale, en tenant compte des plages
        String brancheFinale = getId(branche, thIndex);
        if (brancheFinale == null) {
            throw new IllegalArgumentException("Branche introuvable dans l'index th : " + branche);
        }

        // 3) Charger la table th sous forme de DataPoint
        List<DataPoint> thTable = loadTh();
        DataPoint row = thTable.stream()
                .filter(dp -> dp.getId().equals(brancheFinale))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Branche introuvable dans la table th : " + brancheFinale));

        // 4) Récupérer la valeur pour l'année donnée
        String yearKey = String.valueOf(year);
        Double heures = row.getIndexValues().get(yearKey);
        if (heures == null) {
            throw new IllegalArgumentException(
                    "Année " + year + " non trouvée pour la branche " + brancheFinale);
        }

        return heures;
    }

    /**
     * Calcule le salaire annuel adapté en fonction de la durée hebdo
     * trouvée dans 'th' (p. ex. 41 ou 42h).
     */
    public static double getSalaireAnnuel(Beneficiary benef, double salaireMensuel, String branche) {
        // 1) Récupérer l'année ESS du bénéficiaire
        int year = benef.yearOfEligibility;

        // 2) Récupérer le nombre d'heures hebdo pour la branche
        double heuresNormales = getHeuresHebdo(branche, year);

        // 3) Ajuster le salaire mensuel en fonction de la base 40h
        double salaireMensuelAdj = salaireMensuel * heuresNormales / 40.0;

        // 4) Log pour debug
        updateContext("\nNombre d'heures hebdomadaires selon la branche économique: " + heuresNormales + "  Revenu mensuel ajusté: " + salaireMensuelAdj + " CHF");

        // 5) Retourner le salaire annuel
        return salaireMensuelAdj * 12.0;
    }

    /**
     * Applique la déduction forfaitaire si la capacité de travail globale <= 50 %.
     * Les règles :
     * - avant 2024 => 10 % de déduction si capacité ≤ 50 %, sinon 0 %.
     * - à partir de 2024 => 20 % si capacité ≤ 50 %, sinon 10 %.
     * On y ajoute toujours l’« abattement » (deduction) du bénéficiaire.
     *
     * @param benef  le bénéficiaire (porte les champs yearOfEligibility, activityRate, reduction, deduction)
     * @param montantAnnuel  le revenu annuel brut avant déduction
     * @return le revenu annuel net après application de la déduction
     */
    public static double applyDeduction(Beneficiary benef, double montantAnnuel) {
        // 1) Données de base
        int yearEss = benef.yearOfEligibility;
        double d31 = benef.activityRate / 100.0;    // taux d'activité
        double d32 = benef.reduction / 100.0;       // diminution en %
        double abattement = benef.deduction / 100.0; // abattement forfaitaire

        // 2) Capacité fonctionnelle résiduelle
        double capaciteGlobale = d31 - (d31 * d32);

        // 3) Montant annualisé en fonction de la capacité fonctionnelle
        double montantAjuste = montantAnnuel * capaciteGlobale;

        updateContext(
                "\nTaux d'activité exigible: " + benef.activityRate + "%" +
                "\nDiminution de rendement en pourcent: " + benef.reduction + "%" +
                "\nCapacité fonctionnelle résiduelle: " + capaciteGlobale * 100 + "%" +
                "\nRevenu annuel selon capacité: " + montantAjuste + " CHF"

        );

        // 4) Calcul du taux de déduction supplémentaire
        double deductionRate;
        StringBuilder res = new StringBuilder();

        if (yearEss < 2024) {
            res.append("Déduction supplémentaire au titre de désavantage salarial pour un taux d'activité de 50% ou moins: ");
            if (capaciteGlobale <= 0.5) {
                deductionRate = 0.10;
                res.append("10%");
            } else {
                deductionRate = 0.0;
                res.append("0%");
            }
            // Ajout de l'abattement
            deductionRate += abattement;
            res.append(String.format(
                    "%nAutres déductions au titre de désavantage salarial: %d%%",
                    benef.deduction
            ));
        } else {
            if (capaciteGlobale <= 0.5) {
                deductionRate = 0.20;
            } else {
                deductionRate = 0.10;
            }
            res.append(String.format(
                    "Déduction supplémentaire au titre de désavantage salarial: %.0f%%",
                    deductionRate * 100
            ));
        }

        // 5) Montant net après déduction
        double net = montantAjuste * (1 - deductionRate);
        res.append(String.format("  Revenu après déduction: %.2f CHF", net));

        updateContext(res.toString());
        return net;
    }

    /**
     * Convertit le taux d'invalidité en quotité de rente selon le barème de l’art. 28b LAI.
     *
     * @param tauxInval taux d'invalidité en pourcentage (ex. 45.3)
     * @return quotité de rente correspondante en pourcentage
     * 
     * public static double convertTauxInvaliditeEnRente(double tauxInval) {
     *    if (tauxInval >= 70) {
     *        return 100.0;
     *    } else if (tauxInval >= 50) {
     *        // entre 50 et 69.999… : rente égale au taux
     *        return tauxInval;
     *    } else {
     *        // taux < 50 : barème dégressif
     *        int arrondi = (int) Math.floor(tauxInval);
     *        switch (arrondi) {
     *            case 49: return 47.5;
     *            case 48: return 45.0;
     *            case 47: return 42.5;
     *            case 46: return 40.0;
     *            case 45: return 37.5;
     *            case 44: return 35.0;
     *            case 43: return 32.5;
     *            case 42: return 30.0;
     *            case 41: return 27.5;
     *            case 40: return 25.0;
     *            default:  return 0.0;
     *        }
     *    }
     * }
     */
   

    /**
     * Calcule le salaire avant l'atteinte à la santé en indexant selon la table T1.
     *
     * @param branche code de la branche économique
     * @param taux part d’activité en pourcentage (ex. 80 pour 80%)
     * @param salaire revenu annuel réel selon le taux donné
     * @param annee année du dernier revenu effectif (ex. 2022)
     * @param benef bénéficiaire contenant l’année d’exigibilité, etc.
     * @return revenu effectif annualisé pour 100% d’activité
     */
    public static double getRevenuEffectif(String branche, double taux, double salaire, int annee, Beneficiary benef) {
        // année d'exigibilité (ESS) du bénéficiaire
        int ess = benef.yearOfEligibility;

        // on calcule le revenu annuel simulé pour 100% d’activité
        double salaire100 = 100.0 * salaire / taux;

        // on récupère les indices T1 pour l’année ESS et pour l’année du dernier revenu
        double indexEx    = getIndexFromT1(benef, ess,    branche);
        double indexAnnee = getIndexFromT1(benef, annee, branche);

        // indexation croisée
        double revenuEffectif = indexEx * salaire100 / indexAnnee;

        // affichages pour debug / trace
        updateContext(
                "\nActivité: " + loadLabelsId().get(branche) + branche +
                "\nTaux d'activité: " + taux + "%" +
                "\n\nAnnée du dernier revenu effectif: " + annee+
                "\nRevenu effectif annuel réel: " + salaire
                        + " CHF  Revenu annuel pour un 100%: " + salaire100 + " CHF" +
                "\nAnnée d'exigibilité: " + ess +
                "\nIndexation du revenu:" +
                "\n  " + annee + " -> " + indexAnnee +
                "\n  " + ess + " -> " + indexEx +
                "\n\nRevenu effectif annuel indexé: " + revenuEffectif + " CHF"
        );

        return revenuEffectif;
    }

    /**
     * Calcule le revenu ESS (exigible) indexé selon la branche, l’année et le salaire mensuel de référence.
     *
     * @param benef   le bénéficiaire contenant notamment l’année d’exigibilité (ESS)
     * @param niveau  niveau de compétence (non utilisé ici, mais conservé pour signature)
     * @param revenu  revenu mensuel de référence calculé sur 40h
     * @param annee   année du dernier revenu
     * @param branche code de la branche économique
     * @return revenu ESS annuel indexé
     */
    public static double getRevenuEss(Beneficiary benef, int niveau, double revenu, int annee, String branche) {
        // année ESS du bénéficiaire
        int ess = benef.yearOfEligibility;

        // nombre d'heures hebdo selon la branche et l'année
        updateContext(
                "\nActivité: " + loadLabelsId().get(branche) + branche + "niveau " + niveau +
                "\nAnnée d'exigibilité: " + ess +
                "\nRevenu mensuel calculé sur 40h: " + revenu + " CHF"
        );

        // calcul du salaire annuel ajusté selon les heures
        double revenuAnnuel = getSalaireAnnuel(benef, revenu, branche);

        // récupération des indices T1 pour ESS et pour l'année du dernier revenu
        double indexEx    = getIndexFromT1(benef, ess,    branche);
        double indexAnnee = getIndexFromT1(benef, annee, branche);

        // indexation du revenu ESS annuel
        double revenuEss = indexEx * revenuAnnuel / indexAnnee;

        updateContext(
                "\n\nIndexation du revenu:" +
                "\n  " + annee + " -> " + indexAnnee + " CHF" +
                "\n  " + ess + " -> " + indexEx + " CHF" +
                "\n\nRevenu ESS annuel indexé: " + revenuEss + " CHF"
        );

        return revenuEss;
    }

    /**
     * Calcule le parallélisme entre le revenu avant atteinte à la santé et le revenu ESS indexé.
     *
     * @param revenuAvasta le revenu avant atteinte à la santé (effectif)
     * @param revenuEss    le revenu ESS annuel indexé
     * @return soit le revenu avant atteinte si le ratio est > 95%, sinon 95% du revenu ESS
     */
    public static double getParallelisme(double revenuAvasta, double revenuEss) {
        double difference = revenuAvasta - revenuEss;
        double ratioPct   = revenuAvasta * 100.0 / revenuEss;

        updateContext(
                "\nDifférence entre le revenu effectif et ESS annuel indexé: "
                + difference + " CHF" +
                "\nDifférence en pourcent: " + ratioPct + "%"
        );

        if (ratioPct > 95.0) {
            updateContext("\nLe revenu effectif est supérieur à 95% du revenu statistique.");
            return revenuAvasta;
        } else {
            updateContext("\nLe revenu effectif est inférieur à 95% du revenu statistique.");
            return revenuEss * 0.95;
        }
    }

    /**
     * Calcule le revenu « sans invalidité » pour un bénéficiaire donné.
     *
     * @param benef le bénéficiaire contenant les informations pré et post-santé
     * @return le revenu annuel sans invalidité, après application de toutes les étapes
     */
    public static double getRevenuSansInvalidite(Beneficiary benef) {
        // Branche et infos de salaire « avant santé » (pré-atteinte) :
        StatisticalSalaryInfo sas = benef.preHealthStatisticalSalary;
        EffectiveSalaryInfo eas = benef.preHealthEffectiveSalary;
        // On considère qu'il y a un revenu de référence s'il existe une année et un salaire non nuls
        boolean hadRevenu = sas.year != 0 && sas.salary != 0.0;
        double revenuSainv;

        if (hadRevenu) {
            updateContext("\n\nCalcul du revenu avant l'atteinte à la santé:");

            // revenu effectif basé sur le salaire AS (OFS) et le taux d'activité
            double revenuAvasta = getRevenuEffectif(
                    sas.economicBranch,
                    benef.activityRate,
                    eas.salary,
                    eas.year,
                    benef
            );

            updateContext("\n\nCalcul du revenu exigible selon ESS:");
            // salaire ESS post-santé: on utilise les infos statistiques « postHealthStatisticalSalary »
            double revenuEss = getRevenuEss(
                    benef,
                    sas.skillLevel,
                    sas.salary,
                    sas.year,
                    sas.economicBranch
            );

            updateContext("\n\nCalcul du revenu après parallélisme:");
            revenuSainv = getParallelisme(revenuAvasta, revenuEss);

        } else {
            updateContext("\n\nCalcul du revenu exigible selon ESS:");
            revenuSainv = getRevenuEss(
                    benef,
                    sas.skillLevel,
                    sas.salary,
                    sas.year,
                    sas.economicBranch
            );
        }
        updateContext("\n\nRevenu sans invalidité (final): " + revenuSainv + " CHF");
        return revenuSainv;
    }

    /**
     * Calcule le revenu exigible pour un bénéficiaire donné :
     * - ESS (indexation + déduction forfaitaire)
     * - Revenu effectif (si disponible)
     * Retourne le maximum des deux.
     *
     * @param benef l’objet Beneficiary contenant toutes les infos nécessaires
     * @return le revenu exigible (CHF)
     */
    public static double getRevenuExigible(Beneficiary benef) {
        // données « ESS » (statistiques pré-atteinte)
        StatisticalSalaryInfo exInfo = benef.postHealthStatisticalSalary;
        int exYear          = exInfo.year;
        double exMonthly    = exInfo.salary;
        int exSkillLevel    = exInfo.skillLevel;
        String branche      = exInfo.economicBranch;

        // données « effectif »
        EffectiveSalaryInfo effInfo = benef.postHealthEffectiveSalary;
        boolean hasEffectif = effInfo.salary != 0.0 && effInfo.year != 0;

        updateContext("\n\nCalcul du revenu exigible selon ESS:");
        // 1) calcul ESS + indexation
        double revenuAnnuelESS = getRevenuEss(
                benef,
                exSkillLevel,
                exMonthly,
                exYear,
                branche
        );
        // 2) appliquer la déduction forfaitaire (abattement + taux d’invalidité)
        double revenuESS = applyDeduction(benef, revenuAnnuelESS);

        // 3) calcul du revenu effectif, si disponible
        double revenuEffectif = 0.0;
        if (hasEffectif) {
            updateContext("\n\nCalcul du revenu effectif:");

            revenuEffectif = getRevenuEffectif(
                    branche,
                    benef.activityRate - benef.deduction,
                    effInfo.salary,
                    effInfo.year,
                    benef
            );
        }

        // 4) on prend le maximum
        double revenuFinal = Math.max(revenuESS, revenuEffectif);
        updateContext("\n\nRevenu exigible final: " + revenuFinal + " CHF\n");
        return revenuFinal;
    }

    /**
     * Calcule et affiche le taux d’invalidité basé sur la différence
     * entre le revenu sans invalidité et le revenu exigible.
     *
     * @param benef Map représentant le bénéficiaire, avec sous-maps "sainv" et "ex"
     *              contenant chacune les clés "branche", "niveau_comp", "salaire"…
     * @return un résumé textuel du calcul d’invalidité
     */
    static String getInvalidite(Beneficiary benef) {
        updateContext("Calcul du degré d'invalidité");
        // On assign le salaire TA1 selon la branche le niveau et le sexe
        benef.assignId();
        benef.assignSalaireTa1();

        // Revenu annuel avant invalidité (sans atteinte)
        double revenuSainv = getRevenuSansInvalidite(benef);
        // Revenu exigible (avec atteinte)
        double revenuEx    = getRevenuExigible(benef);

        // Calcul du taux d'invalidité
        double perte = revenuSainv - revenuEx;
        double tauxInval = perte * 100.0 / revenuSainv;
        double tauxArrondi = Math.round(tauxInval * 100.0) / 100.0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Revenu sans invalidité: %.2f CHF, Revenu exigible: %.2f CHF\n",
                revenuSainv, revenuEx));
        sb.append(String.format("Perte de revenu sur 100%%: %.2f CHF\n", perte));
        sb.append(String.format("Taux d’invalidité dans la partie lucrative: %.2f%%\n\n",
                tauxInval));
        sb.append("Degré d’invalidité: ").append(tauxArrondi).append("%");

        updateContext(sb.toString());

        return getFullContext().toString();
    }
}
