package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.List;

public final class IndexTH implements Index {

    private static final List<String> IDS = List.of("01-96", "01-03", "05-43", "05-09", "10-33", "10-12", "13-15", "16-18", "19-20", "21", "22-23", "24-25", "26", "27", "28", "29-30", "31-33", "35", "36-39", "41-43", "41-42", "43", "45-96", "45-47", "45", "46", "47", "49-53", "49", "50-51", "52", "53", "55-56", "55", "56", "58-63", "58-60", "61", "62-63", "64-66", "64", "65", "66", "68", "69-75", "69", "70", "71", "72", "73-75", "77-82", "77+79-82", "78", "84", "85", "86-88", "86", "87", "88", "90-93", "94-96");

    @Override
    public String totalId() {
        return "01-96";
    }

    @Override
    public List<String> ids() {
        return IDS;
    }
}
