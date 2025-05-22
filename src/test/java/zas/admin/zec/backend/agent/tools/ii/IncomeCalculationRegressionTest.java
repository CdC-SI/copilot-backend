package zas.admin.zec.backend.agent.tools.ii;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import zas.admin.zec.backend.TestConfig;
import zas.admin.zec.backend.agent.tools.ii.model.Beneficiary;
import zas.admin.zec.backend.agent.tools.ii.model.Gender;
import zas.admin.zec.backend.agent.tools.ii.model.Salary;
import zas.admin.zec.backend.agent.tools.ii.service.IncomeCalculationService;

import java.math.BigDecimal;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Import(TestConfig.class)
@SpringBootTest
class IncomeCalculationRegressionTest {
    @Autowired
    private IncomeCalculationService incomeCalculationService;

    @Test
    void legacy_and_new_implementations_match() {
        /* -----------------------------------------------------------------
           1) Construire le bénéficiaire pour l’ANCIEN monolithe
           ----------------------------------------------------------------- */
        IncomeCalculation.Beneficiary legacy =
                new IncomeCalculation.Beneficiary(
                        2025,                       // yearOfEligibility
                        "homme",                   // gender
                        new IncomeCalculation.EffectiveSalaryInfo(2023, 78_000),
                        new IncomeCalculation.StatisticalSalaryInfo(
                                2022, 0, 3, "10-33"),             // pré-santé
                        new IncomeCalculation.EffectiveSalaryInfo(2024, 46_000),
                        new IncomeCalculation.StatisticalSalaryInfo(
                                2022, 0, 3, "10-33"),             // post-santé
                        100, 20, 5);               // activityRate, reduction, deduction

        String legacyResult = IncomeCalculation.getInvalidite(legacy);
        double legacyDegree  = extractDegree(legacyResult);

        /* -----------------------------------------------------------------
           2) Construire le bénéficiaire pour la NOUVELLE API
           ----------------------------------------------------------------- */
        Beneficiary modern = new Beneficiary(
                Year.of(2025),
                Gender.MALE,
                100,   // activityRate
                20,    // performanceLoss
                5,     // additionalDeduction
                "10-33", // branchId
                3,     // skillLevelBefore
                3,     // skillLevelAfter
                new Salary(Year.of(2023), BigDecimal.valueOf(78_000)),
                new Salary(Year.of(2024), BigDecimal.valueOf(46_000))
        );

        /* -----------------------------------------------------------------
           3) Comparaison
           ----------------------------------------------------------------- */
        double modernDegree = incomeCalculationService.invalidityDegreeAsDouble(modern);

        assertThat(modernDegree)
                .as("Le nouveau service doit reproduire exactement l’ancienne logique")
                .isCloseTo(legacyDegree, within(0.001));
    }

    private static double extractDegree(String legacyText) {
        // « Degré d’invalidité : 37.50% »
        int idxstart = legacyText.lastIndexOf(":** ");
        int idxend = legacyText.lastIndexOf("%");
        String pct = legacyText.substring(idxstart + 4, idxend).replace(',', '.');
        return Double.parseDouble(pct);
    }
}
