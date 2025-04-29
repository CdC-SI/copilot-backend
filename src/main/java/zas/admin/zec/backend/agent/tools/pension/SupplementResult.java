package zas.admin.zec.backend.agent.tools.pension;

public final class SupplementResult implements CalculResult {

    private static final String RESPONSE_FR = "Votre supplément de rente mensuel s’élève à %.2f CHF.";
    private static final String RESPONSE_IT = "Il suo supplemento mensile di rendita ammonta a %.2f CHF.";
    private static final String RESPONSE_DE = "Ihre monatliche Rentenzulage beträgt %.2f CHF.";

    private final String lang;
    private final double supplement;

    public SupplementResult(String lang, double supplement) {
        this.lang = lang;
        this.supplement = supplement;
    }

    @Override
    public String result() {
        return switch (lang) {
            case "fr" -> RESPONSE_FR.formatted(supplement);
            case "it" -> RESPONSE_IT.formatted(supplement);
            default -> RESPONSE_DE.formatted(supplement);
        };
    }
}
