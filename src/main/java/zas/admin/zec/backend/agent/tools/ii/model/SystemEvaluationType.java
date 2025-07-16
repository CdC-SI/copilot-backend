package zas.admin.zec.backend.agent.tools.ii.model;

public enum SystemEvaluationType {

    LINEAR("Rente linéaire"), BY_STEP("Rente par pallier"), UNKNOWN("Système inconnu");

    private final String type;

    SystemEvaluationType(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }
}
