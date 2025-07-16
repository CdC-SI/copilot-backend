package zas.admin.zec.backend.agent.tools.pension;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

public class PensionTools {

    public static final String LANG = "lang";
    private static final LocalDate MIN_DOB = LocalDate.of(1961, 1, 1);
    private static final LocalDate MAX_DOB = LocalDate.of(1969, 12, 31);
    private static final double[][] REDUCTION_RATES = {{0, 2.5, 3.5}, {2, 4.5, 6.5}, {3, 6.5, 10.5}};
    private static final Map<Integer, Integer> REFERENCE_AGES = Map.of(
            1961, 64 * 12 + 3,
            1962, 64 * 12 + 6,
            1963, 64 * 12 + 9,
            1964, 65 * 12,
            1965, 65 * 12,
            1966, 65 * 12,
            1967, 65 * 12,
            1968, 65 * 12,
            1969, 65 * 12
    );
    private static final Map<Integer, Integer> SUPPLEMENT_PERCENTAGES = Map.of(
            1961, 25,
            1962, 50,
            1963, 75,
            1964, 100,
            1965, 100,
            1966, 81,
            1967, 63,
            1968, 44,
            1969, 25
    );

    @Tool(
            name = "calculate_reduction_rate_and_supplement",
            description = "Calculate the reduction rate or pension supplement for women of the transitional generation."
    )
    String calculateReductionRateAndSupplement(
            @ToolParam(description = "Birth date for women born between 1961 and 1969") LocalDate birthDate,
            @ToolParam(description = "Planned retirement date") LocalDate retirementDate,
            @ToolParam(description = "Average annual income in CHF (minimum 0)") Double averageAnnualIncome,
            ToolContext toolContext) {

        var lang = (String) toolContext.getContext().getOrDefault(LANG, "de");
        return calculate(birthDate, retirementDate, averageAnnualIncome, lang).result();
    }

    private CalculResult calculate(LocalDate birthDate, LocalDate retirementDate, Double averageAnnualIncome, String lang) {
        if (birthDate.isBefore(MIN_DOB) || birthDate.isAfter(MAX_DOB)) {
            return new NotEligibleResult(lang);
        }

        var period = Period.between(birthDate, retirementDate);
        var periodMonths = period.getYears() * 12 + period.getMonths();
        var referenceMonths = REFERENCE_AGES.get(birthDate.getYear());
        var incomeStats = getIncomeStats(averageAnnualIncome);

        if (periodMonths >= referenceMonths) {
            var percentage = SUPPLEMENT_PERCENTAGES.get(birthDate.getYear());
            var baseSupplement = incomeStats.baseSupplement * percentage / 100;
            return new SupplementResult(lang, baseSupplement);
        } else {
            int anticipation = (int) Math.round((referenceMonths - periodMonths) / 12.0);
            if (anticipation < 1 || anticipation > 3) {
                return new InvalidAnticipationResult(lang);
            }
            var reductionRate = REDUCTION_RATES[anticipation - 1][incomeStats.bracket - 1];
            return new ReductionRateResult(lang, reductionRate);
        }
    }

    record IncomeStats(int bracket, double baseSupplement) {}
    private IncomeStats getIncomeStats(double averageAnnualIncome) {
        if (averageAnnualIncome <= 60480) {
            return new IncomeStats(1, 160);
        } else if (averageAnnualIncome <= 75600) {
            return new IncomeStats(2, 100);
        } else {
            return new IncomeStats(3, 50);
        }
    }
}
