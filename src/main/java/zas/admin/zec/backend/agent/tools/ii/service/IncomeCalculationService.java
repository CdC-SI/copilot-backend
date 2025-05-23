package zas.admin.zec.backend.agent.tools.ii.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.agent.tools.ii.model.*;
import zas.admin.zec.backend.agent.tools.ii.repository.DataRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.Map;

import static zas.admin.zec.backend.agent.tools.ii.utils.RangeHelper.matchingIdForTarget;

@Slf4j
@Service
public class IncomeCalculationService {

    private final Ta1LookupService ta1LookupService;
    private final IndexationService indexationService;
    private final HoursService hoursService;
    private final DeductionService deductionService;
    private final DataRepository repo;

    public IncomeCalculationService(Ta1LookupService ta1LookupService,
                                    IndexationService indexationService,
                                    HoursService hoursService,
                                    DeductionService deductionService,
                                    DataRepository repo) {

        this.ta1LookupService = ta1LookupService;
        this.indexationService = indexationService;
        this.hoursService = hoursService;
        this.deductionService = deductionService;
        this.repo = repo;
    }

    public double disabilityDegree(Beneficiary beneficiary) {
        var withoutDisability = computeSalaryWithoutDisability(beneficiary);
        var withDisability = computeSalaryWithDisability(beneficiary);
        var disabilityRate = withoutDisability.subtract(withDisability)
                .multiply(BigDecimal.valueOf(100))
                .divide(withoutDisability, 2, RoundingMode.HALF_UP)
                .doubleValue();

        log.info("\nsalary pre health {}\nsalary post health {}\ndisability rate {}", withoutDisability.doubleValue(), withDisability.doubleValue(), disabilityRate);
        return Math.round(disabilityRate * 100.0) / 100.0;
    }

    public BigDecimal computeSalaryWithoutDisability(Beneficiary beneficiary) {
        var effectiveSalary = computePreHealthEffectiveSalary(beneficiary);
        var payableSalary = computePreHealthPayableSalary(beneficiary);
        return computeParallelism(effectiveSalary, payableSalary);
    }

    public BigDecimal computeSalaryWithDisability(Beneficiary beneficiary) {
        var effectiveSalary = computePostHealthEffectiveSalary(beneficiary);
        var payableSalary = computePostHealthPayableSalary(beneficiary);
        return effectiveSalary.max(payableSalary);
    }

    private BigDecimal computePreHealthEffectiveSalary(Beneficiary beneficiary) {
        return computeEffectiveSalary(beneficiary.gender(), beneficiary.eligibilityYear(), beneficiary.preHealthDetails(), beneficiary.activityRate());
    }

    private BigDecimal computePostHealthEffectiveSalary(Beneficiary beneficiary) {
        return computeEffectiveSalary(beneficiary.gender(), beneficiary.eligibilityYear(), beneficiary.postHealthDetails(), beneficiary.activityRate() - beneficiary.activityReduction());
    }

    private BigDecimal computePreHealthPayableSalary(Beneficiary beneficiary) {
        return computePayableSalary(beneficiary.gender(), beneficiary.eligibilityYear(), beneficiary.preHealthDetails());
    }

    private BigDecimal computePostHealthPayableSalary(Beneficiary beneficiary) {
        var payableSalary = computePayableSalary(beneficiary.gender(), beneficiary.eligibilityYear(), beneficiary.postHealthDetails());
        return deductionService.apply(beneficiary, payableSalary);
    }

    private BigDecimal computeEffectiveSalary(Gender gender, Year eligibilityYear, BeneficiaryDetails details, int activityRate) {
        var branchId = convertLiteralBranchToId(details.economicBranch());
        var matchingBranch = matchingIdForTarget(branchId, new IndexHF());

        var indexForEligibilityYear = indexationService.index(gender, matchingBranch, eligibilityYear);
        var indexForLastEffectiveSalaryYear = indexationService.index(gender, matchingBranch, details.salary().referenceYear());

        return details.salary().amount()
                .multiply(BigDecimal.valueOf(indexForEligibilityYear))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(indexForLastEffectiveSalaryYear), 2, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(activityRate), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal computePayableSalary(Gender gender, Year eligibilityYear, BeneficiaryDetails details) {
        var branchId = convertLiteralBranchToId(details.economicBranch());
        var matchingBranchHF = matchingIdForTarget(branchId, new IndexHF());
        var matchingBranchTA = matchingIdForTarget(branchId, new IndexTA());
        var matchingBranchTH = matchingIdForTarget(branchId, new IndexTH());

        var indexForEligibilityYear = indexationService.index(gender, matchingBranchHF, eligibilityYear);
        var indexForStatisticalYear = indexationService.index(gender, matchingBranchHF, Year.of(2022));
        BigDecimal statisticalMonthlySalary = ta1LookupService.salary(matchingBranchTA, details.skillLevel(), gender);

        return hoursService.annualSalary(eligibilityYear, statisticalMonthlySalary, matchingBranchTH)
                .multiply(BigDecimal.valueOf(indexForEligibilityYear))
                .divide(BigDecimal.valueOf(indexForStatisticalYear), 2, RoundingMode.HALF_UP);
    }

    private String convertLiteralBranchToId(String branch) {
        return repo.loadLabels()
                .entrySet()
                .stream()
                .filter(entry -> similarityMatch(branch, entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(branch);
    }

    private boolean similarityMatch(String id1, String id2) {
        return new LevenshteinDistance(4).apply(id1, id2) != -1;
    }

    /**
     * Compute the parallelism between the effective salary and the payable salary.
     *
     * @param effectiveSalary salary before the health issue
     * @param payableSalary annual indexed salary
     * @return the effective salary if it is greater than 95% of the payable salary, otherwise 95% of the payable salary
     */
    private BigDecimal computeParallelism(BigDecimal effectiveSalary, BigDecimal payableSalary) {
        BigDecimal parallelism = payableSalary.multiply(BigDecimal.valueOf(0.95));
        if (effectiveSalary.compareTo(parallelism) > 0) {
            return effectiveSalary;
        }
        return parallelism;
    }
}
