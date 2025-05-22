package zas.admin.zec.backend.agent.tools.ii.model;

public enum TableKind {
    T1_MALE("T1_male.csv"),
    T1_FEMALE("T1_female.csv"),
    T1_RAI("T1_rai.csv"),
    TA1("TA1.csv"),
    TH("TH.csv");

    private final String file;
    TableKind(String file) { this.file = file; }
    public String file() { return file; }
}