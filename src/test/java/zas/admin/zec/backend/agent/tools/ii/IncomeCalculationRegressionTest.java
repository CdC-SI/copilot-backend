package zas.admin.zec.backend.agent.tools.ii;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zas.admin.zec.backend.agent.tools.ii.legacy.IncomeCalculation;
import zas.admin.zec.backend.agent.tools.ii.model.Beneficiary;
import zas.admin.zec.backend.agent.tools.ii.model.BeneficiaryDetails;
import zas.admin.zec.backend.agent.tools.ii.model.Gender;
import zas.admin.zec.backend.agent.tools.ii.model.Salary;
import zas.admin.zec.backend.agent.tools.ii.service.IncomeCalculationService;

import java.math.BigDecimal;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
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
                        2022,                       // yearOfEligibility
                        "homme",                   // gender
                        new IncomeCalculation.EffectiveSalaryInfo(2020, 100_000),
                        new IncomeCalculation.StatisticalSalaryInfo(
                                2022, 0, 4, "Industries extractives"),             // pré-santé
                        new IncomeCalculation.EffectiveSalaryInfo(2021, 70_000),
                        new IncomeCalculation.StatisticalSalaryInfo(
                                2022, 0, 2, "Industrie manufacturière"),             // post-santé
                        100, 70, 0);               // activityRate, reduction, deduction

        String legacyResult = IncomeCalculation.getInvalidite(legacy);
        double legacyDegree  = extractDegree(legacyResult);

        /* -----------------------------------------------------------------
           2) Construire le bénéficiaire pour la NOUVELLE API
           ----------------------------------------------------------------- */
        Beneficiary modern = new Beneficiary(
                Year.of(2022),
                Gender.MALE,
                new BeneficiaryDetails(
                        new Salary(Year.of(2020), BigDecimal.valueOf(100_000)),
                        "Industries extractives",
                        4
                ),
                new BeneficiaryDetails(
                        new Salary(Year.of(2021), BigDecimal.valueOf(70_000)),
                        "Industrie manufacturière",
                        2
                ),
                100,
                70,
                0
        );

        /* -----------------------------------------------------------------
           3) Comparaison
           ----------------------------------------------------------------- */
        double modernDegree = incomeCalculationService.disabilityDegree(modern).disabilityRate();

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
