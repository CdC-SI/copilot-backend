package zas.admin.zec.backend.agent.tools.ii.service;

class DeductionServiceTest {
/*
    private DeductionService service;

    @Test
    void calculatesCorrectDeductionForHappyPath() {
        BigDecimal annualSalary = BigDecimal.valueOf(100000);
        BigDecimal result = service.apply(beneficiary, annualSalary);
        assertEquals(0, BigDecimal.valueOf(57600).compareTo(result));
    }

    @Test
    void returnsZeroWhenGlobalCapacityIsZeroDueToZeroActivityRate() {
        DeductionService service = new TestDeductionService(strategy);
        DummyBeneficiary beneficiary = new DummyBeneficiary(0, 0, Year.of(2023));
        BigDecimal annualSalary = BigDecimal.valueOf(100000);
        BigDecimal result = service.apply(beneficiary, annualSalary);
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void returnsZeroWhenGlobalCapacityIsZeroDueToFullReduction() {
        DummyStrategy strategy = (ben, globalCapacity) -> BigDecimal.valueOf(0.2);
        DeductionService service = new TestDeductionService(strategy);
        DummyBeneficiary beneficiary = new DummyBeneficiary(80, 100, Year.of(2023));
        BigDecimal annualSalary = BigDecimal.valueOf(100000);
        BigDecimal result = service.apply(beneficiary, annualSalary);
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void appliesAdjustedSalaryWhenDeductionRateIsZero() {
        DummyStrategy strategy = (ben, globalCapacity) -> BigDecimal.ZERO;
        DeductionService service = new TestDeductionService(strategy);
        DummyBeneficiary beneficiary = new DummyBeneficiary(70, 10, Year.of(2023));
        BigDecimal annualSalary = BigDecimal.valueOf(100000);
        BigDecimal result = service.apply(beneficiary, annualSalary);
        assertEquals(0, BigDecimal.valueOf(63000).compareTo(result));
    }

    @Test
    void calculatesLowerFinalSalaryWithHighDeductionRate() {
        DummyStrategy strategy = (ben, globalCapacity) -> BigDecimal.valueOf(0.95);
        DeductionService service = new TestDeductionService(strategy);
        DummyBeneficiary beneficiary = new DummyBeneficiary(90, 10, Year.of(2023));
        BigDecimal annualSalary = BigDecimal.valueOf(200000);
        BigDecimal result = service.apply(beneficiary, annualSalary);
        assertEquals(0, BigDecimal.valueOf(8100).compareTo(result));
    }

    private Beneficiary dummyBeneficiary(int activityRate, int activityReduction, Year eligibilityYear) {

    }

 */
}