package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.List;

public final class IndexRAI implements Index {

    private static final List<String> IDS = List.of("05-96", "01-03", "05-43", "05-09", "35-39", "10-33", "10-12", "16-18", "19-21", "22-23", "24-25", "26-27", "28-30", "31-33", "41-43", "45-96", "45-47", "45", "46", "47", "49-53", "49-52", "53", "55-56", "58-63", "58-61", "62-63", "64-66", "64/66", "65", "69-75", "77-82", "84", "86-88", "90-96");

    @Override
    public String totalId() {
        return "05-96";
    }

    @Override
    public List<String> ids() {
        return IDS;
    }
}
