package zas.admin.zec.backend.agent.tools.ii.service;

import zas.admin.zec.backend.agent.tools.ii.model.Beneficiary;

import java.math.BigDecimal;
import java.time.Year;

public class DeductionStrategyFactory {

    public sealed interface DeductionStrategy permits DeductionBefore2024, DeductionStartingFrom2024 {
        BigDecimal compute(Beneficiary b, double capacity);
    }

    public DeductionStrategy forYear(Year y) {
        return y.isBefore(Year.of(2024))
                ? new DeductionBefore2024()
                : new DeductionStartingFrom2024();
    }

    private static final class DeductionBefore2024 implements DeductionStrategy {
        @Override
        public BigDecimal compute(Beneficiary b, double capacity) {
            var deductionPer = b.additionalDeduction() / 100.0;
            return capacity <= 0.5
                    ? BigDecimal.valueOf(deductionPer + 0.10)
                    : BigDecimal.valueOf(deductionPer);
        }
    }

    private static final class DeductionStartingFrom2024 implements DeductionStrategy {
        @Override
        public BigDecimal compute(Beneficiary b, double capacity) {
            return capacity <= 0.5
                    ? BigDecimal.valueOf(0.20)
                    : BigDecimal.valueOf(0.10);
        }
    }
}
