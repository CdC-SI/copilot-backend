package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.List;

public final class IndexHF implements Index {

    private static final List<String> IDS = List.of("05-96", "01-03", "05-43", "05-09/35-39", "10-33", "41-43", "45-96", "45-47", "49-53", "55/56", "58-63", "64-66", "69-75", "77-82", "84", "86-88", "90-96");

    @Override
    public String totalId() {
        return "05-96";
    }

    @Override
    public List<String> ids() {
        return IDS;
    }
}
