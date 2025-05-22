package zas.admin.zec.backend.agent.tools.ii.service;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.agent.tools.ii.model.Beneficiary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;

@Service
public class DeductionService {

    /** Retourne le revenu net après déductions. */
    public BigDecimal apply(Beneficiary b, BigDecimal annualGross) {

        /* 1) Capacité fonctionnelle résiduelle -------------------------- */
        BigDecimal activityRate   = pct(b.activityRate());       // ex. 1.00
        BigDecimal performanceLoss= pct(b.performanceLoss());    // ex. 0.20
        BigDecimal capacity       = activityRate.multiply(
                BigDecimal.ONE.subtract(performanceLoss));

        /* 2) Taux de déduction supplémentaire -------------------------- */
        BigDecimal extra;
        Year essYear = b.eligibilityYear();

        if (essYear.isBefore(Year.of(2024))) {
            extra = capacity.compareTo(BigDecimal.valueOf(0.50)) <= 0
                    ? pct(10)  // 10 %
                    : BigDecimal.ZERO;
            extra = extra.add(pct(b.additionalDeduction()));     // abattement
        } else {
            extra = capacity.compareTo(BigDecimal.valueOf(0.50)) <= 0
                    ? pct(20)  // 20 %
                    : pct(10); // 10 %
        }

        /* 3) Net -------------------------------------------------------- */
        BigDecimal adjusted = annualGross.multiply(capacity);
        return adjusted.multiply(BigDecimal.ONE.subtract(extra))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 42 → 0.42 sous forme BigDecimal (10 décimales, HALF_UP). */
    private static BigDecimal pct(int percentage) {
        return BigDecimal.valueOf(percentage)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
    }
}

