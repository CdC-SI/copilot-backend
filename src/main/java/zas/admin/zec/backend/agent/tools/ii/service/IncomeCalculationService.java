package zas.admin.zec.backend.agent.tools.ii.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.agent.tools.ii.model.Beneficiary;
import zas.admin.zec.backend.agent.tools.ii.model.Gender;
import zas.admin.zec.backend.agent.tools.ii.model.Salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class IncomeCalculationService {

    private final IndexationService indexSrv;   // indices T1
    private final HoursService      hoursSrv;   // heures hebdo TH
    private final DeductionService  deductSrv;  // abattements & déductions
    private final Ta1LookupService  ta1Srv;     // salaires statistiques TA1

    /* ------------------------------------------------------------------ *
     *  Outils privés                                                     *
     * ------------------------------------------------------------------ */

    /** Salaire annuel (CHF) ajusté aux heures hebdo réelles de la branche. */
    private BigDecimal annual40hToAnnual(Salary monthly40h,
                                         String branchId, Year year) {

        BigDecimal hours = BigDecimal.valueOf(
                hoursSrv.weeklyHours(branchId, year));

        return monthly40h.amountMonthly()
                .multiply(hours)               // passer de 40h → heures réelles
                .divide(BigDecimal.valueOf(40), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(12)); // mensuel → annuel
    }

    /** Indexation croisée (T1) entre deux années. */
    private BigDecimal index(BigDecimal amount,
                             String branchId,
                             Year from, Year to,
                             Gender g) {

        BigDecimal idxFrom = BigDecimal.valueOf(indexSrv.index(g, branchId, from));
        BigDecimal idxTo   = BigDecimal.valueOf(indexSrv.index(g, branchId, to));

        return amount.multiply(idxTo)
                .divide(idxFrom, 10, RoundingMode.HALF_UP);
    }

    /* ------------------------------------------------------------------ *
     *  Revenu sans invalidité                                            *
     * ------------------------------------------------------------------ */

    public BigDecimal incomeWithoutDisability(Beneficiary b) {

        /* salaire effectif avant atteinte (100 %) ---------------------- */
        BigDecimal eff100 = b.effectiveBefore().amountMonthly()
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(b.activityRate()), 10, RoundingMode.HALF_UP);

        BigDecimal effIndexed = index(
                eff100, b.branchId(),
                b.effectiveBefore().referenceYear(),
                b.eligibilityYear(), b.gender());

        /* salaire statistique TA1 avant atteinte ----------------------- */
        BigDecimal statMonthly = ta1Srv.salary(
                b.branchId(), b.skillLevelBefore(), b.gender());

        BigDecimal statAnnual  = annual40hToAnnual(
                new Salary(b.eligibilityYear(), statMonthly),
                b.branchId(), b.eligibilityYear());

        /* parallélisme 95 % ------------------------------------------- */
        BigDecimal ratio = effIndexed.divide(statAnnual, 10, RoundingMode.HALF_UP);
        return (ratio.compareTo(new BigDecimal("0.95")) >= 0)
                ? effIndexed
                : statAnnual.multiply(new BigDecimal("0.95"));
    }

    /* ------------------------------------------------------------------ *
     *  Revenu exigible (avec invalidité)                                 *
     * ------------------------------------------------------------------ */

    public BigDecimal incomeExigible(Beneficiary b) {

        /* ESS statistique après atteinte ------------------------------- */
        BigDecimal statMonthly = ta1Srv.salary(
                b.branchId(), b.skillLevelAfter(), b.gender());

        BigDecimal statAnnual  = annual40hToAnnual(
                new Salary(b.eligibilityYear(), statMonthly),
                b.branchId(), b.eligibilityYear());

        BigDecimal essIndexed = index(
                statAnnual, b.branchId(),
                b.effectiveAfter().referenceYear(),
                b.eligibilityYear(), b.gender());

        BigDecimal essNet = deductSrv.apply(b, essIndexed);

        /* Revenu effectif après atteinte (si présent) ------------------ */
        BigDecimal effNet = BigDecimal.ZERO;
        if (b.effectiveAfter().amountMonthly().compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal annual40 = annual40hToAnnual(
                    b.effectiveAfter(), b.branchId(),
                    b.effectiveAfter().referenceYear());

            effNet = index(
                    annual40, b.branchId(),
                    b.effectiveAfter().referenceYear(),
                    b.eligibilityYear(), b.gender());
        }

        /* On retient le plus élevé ------------------------------------ */
        return essNet.max(effNet);
    }

    /* ------------------------------------------------------------------ *
     *  Degré d’invalidité                                                *
     * ------------------------------------------------------------------ */

    public BigDecimal invalidityDegree(Beneficiary b) {

        BigDecimal without = incomeWithoutDisability(b);
        BigDecimal with    = incomeExigible(b);

        BigDecimal lossPct = without.subtract(with)
                .multiply(BigDecimal.valueOf(100))
                .divide(without, 4, RoundingMode.HALF_UP);

        return lossPct.setScale(2, RoundingMode.HALF_UP); // ex. 37.50
    }

    /** Wrapper « legacy » qui renvoie un double pour les anciens tests. */
    public double invalidityDegreeAsDouble(Beneficiary b) {
        return invalidityDegree(b).doubleValue();
    }
}

