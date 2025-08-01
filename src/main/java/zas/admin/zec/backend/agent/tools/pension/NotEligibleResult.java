package zas.admin.zec.backend.agent.tools.pension;

public final class NotEligibleResult implements CalculResult {

    private static final String RESPONSE_FR = """
            Vous ne remplissez pas les conditions d'éligibilité.
            Pour plus d'informations, consultez: [Taux de réduction favorable en cas d’anticipation de la rente](https://www.eak.admin.ch/eak/fr/home/dokumentation/pensionierung/reform-ahv21/kuerzungssaetze-bei-vorbezug.html)
            """;

    private static final String RESPONSE_IT = """
            Non soddisfa i requisiti di ammissibilità.
            Per maggiori informazioni, consulti: [Aliquote di riduzione ridotte in caso di anticipazione della rendita](https://www.eak.admin.ch/eak/it/home/dokumentation/pensionierung/reform-ahv21/kuerzungssaetze-bei-vorbezug.html")
            """;

    private static final String RESPONSE_DE = """
            Sie erfüllen die Anspruchsvoraussetzungen nicht.
            Weitere Informationen finden Sie unter: [Tiefere Kürzungssätze bei Vorbezug](https://www.eak.admin.ch/eak/de/home/dokumentation/pensionierung/reform-ahv21/kuerzungssaetze-bei-vorbezug.html)
            """;

    private final String lang;

    public NotEligibleResult(String lang) {
        this.lang = lang;
    }

    @Override
    public String result() {
        return switch (lang) {
            case "fr" -> RESPONSE_FR;
            case "it" -> RESPONSE_IT;
            default -> RESPONSE_DE;
        };
    }
}
