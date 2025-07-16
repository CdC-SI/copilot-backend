package zas.admin.zec.backend.agent.tools.pension;

public final class ReductionRateResult implements CalculResult {

    private static final String RESPONSE_FR = "Votre taux de réduction est de %.2f%%.";
    private static final String RESPONSE_IT = "Il suo tasso di riduzione è del %.2f%%.";
    private static final String RESPONSE_DE = "Ihr Kürzungssatz beträgt %.2f%%.";

    private final String lang;
    private final double reductionRate;

    public ReductionRateResult(String lang, double reductionRate) {
        this.lang = lang;
        this.reductionRate = reductionRate;
    }

    @Override
    public String result() {
        return switch (lang) {
            case "fr" -> RESPONSE_FR.formatted(reductionRate);
            case "it" -> RESPONSE_IT.formatted(reductionRate);
            default -> RESPONSE_DE.formatted(reductionRate);
        };
    }
}
