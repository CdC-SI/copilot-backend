package zas.admin.zec.backend.agent.tools.ii.service;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.agent.tools.ii.model.Beneficiary;

import java.math.BigDecimal;

@Service
public class DeductionService {

    public BigDecimal apply(Beneficiary beneficiary, BigDecimal annualSalary) {
        var activityRatePer = beneficiary.activityRate() / 100.0;
        var reductionPer = beneficiary.activityReduction() / 100.0;
        var globalCapacity = activityRatePer - (activityRatePer * reductionPer);
        var adjustedSalary = annualSalary.multiply(BigDecimal.valueOf(globalCapacity));
        var deductionRate = new DeductionStrategyFactory()
                .forYear(beneficiary.eligibilityYear())
                .compute(beneficiary, globalCapacity);

        return adjustedSalary.multiply(BigDecimal.ONE.subtract(deductionRate));
    }
}

