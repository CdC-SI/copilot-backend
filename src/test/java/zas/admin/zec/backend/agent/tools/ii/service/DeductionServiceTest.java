package zas.admin.zec.backend.agent.tools.ii.service;

import org.junit.jupiter.api.Test;
import zas.admin.zec.backend.agent.tools.ii.model.Beneficiary;
import zas.admin.zec.backend.agent.tools.ii.model.BeneficiaryDetails;
import zas.admin.zec.backend.agent.tools.ii.model.Gender;
import zas.admin.zec.backend.agent.tools.ii.model.Salary;

import java.math.BigDecimal;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class DeductionServiceTest {

    private final DeductionService service = new DeductionService();

    @Test
    void calculatesCorrectDeductionForHappyPath() {
        var beneficiary = dummyBeneficiary(80, 20, 0, Year.of(2023));
        var annualSalary = BigDecimal.valueOf(100_000);
        var result = service.computeAdjustedSalary(beneficiary, annualSalary);
        assertThat(result).isCloseTo(BigDecimal.valueOf(64_000), withPercentage(0));
    }

    @Test
    void addTheAdditionalDeductionWhenBefore2024() {
        var beneficiary = dummyBeneficiary(80, 20, 10, Year.of(2023));
        var annualSalary = BigDecimal.valueOf(100_000);
        var result = service.computeAdjustedSalary(beneficiary, annualSalary);
        assertThat(result).isCloseTo(BigDecimal.valueOf(57_600), withPercentage(0));
    }

    @Test
    void returnsZeroWhenGlobalCapacityIsZeroDueToZeroActivityRate() {
        var beneficiary = dummyBeneficiary(0, 0, 0, Year.of(2023));
        BigDecimal annualSalary = BigDecimal.valueOf(100_000);
        BigDecimal result = service.computeAdjustedSalary(beneficiary, annualSalary);
        assertThat(result).isCloseTo(BigDecimal.ZERO, withPercentage(0));
    }

    @Test
    void returnsZeroWhenGlobalCapacityIsZeroDueToFullReduction() {
        var beneficiary = dummyBeneficiary(80, 100, 0, Year.of(2023));
        var annualSalary = BigDecimal.valueOf(100_000);
        var result = service.computeAdjustedSalary(beneficiary, annualSalary);
        assertThat(result).isCloseTo(BigDecimal.ZERO, withPercentage(0));
    }

    @Test
    void after2024TheAdditionalDeductionIsIgnoredAndBasedOnTheGlobalCapacity1() {
        var beneficiary = dummyBeneficiary(100, 10, 15, Year.of(2024));
        var annualSalary = BigDecimal.valueOf(100_000);

        // Capacity > 50% -> deduction should be 10% (on top of the 10% activity reduction)
        var result = service.computeAdjustedSalary(beneficiary, annualSalary);
        assertThat(result).isCloseTo(BigDecimal.valueOf(81_000), withPercentage(0));
    }

    @Test
    void after2024TheAdditionalDeductionIsIgnoredAndBasedOnTheGlobalCapacity2() {
        var beneficiary = dummyBeneficiary(100, 50, 0, Year.of(2024));
        var annualSalary = BigDecimal.valueOf(100_000);

        // Capacity < 50% -> deduction should be 20% (on top of the 50% activity reduction)
        var result = service.computeAdjustedSalary(beneficiary, annualSalary);
        assertThat(result).isCloseTo(BigDecimal.valueOf(40_000), withPercentage(0));
    }

    private Beneficiary dummyBeneficiary(int activityRate, int activityReduction, int additionalDeduction, Year eligibilityYear) {
        return new Beneficiary(
                eligibilityYear,
                Gender.MALE,
                new BeneficiaryDetails(
                        new Salary(Year.of(2023), BigDecimal.valueOf(100_000)),
                        "Fabrication d’équipements électriques",
                        3
                ),
                new BeneficiaryDetails(
                        new Salary(Year.of(2024), BigDecimal.valueOf(90_000)),
                        "Industries extractives",
                        2
                ),
                activityRate,
                activityReduction,
                additionalDeduction
        );
    }
}