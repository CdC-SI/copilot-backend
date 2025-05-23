package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.List;

public final class IndexTA implements Index {

    private static final List<String> IDS =  List.of("01-96", "05-43", "05-09", "10-33", "10-11", "12", "13-15", "16-18", "19-20", "21", "22-23", "24-25", "26", "27", "28", "29-30", "31-33", "35", "36-39", "41-43", "45-96", "45-47", "45-46", "47", "49-53", "49-52", "53", "55-56", "58-63", "58-60", "61", "62-63", "64-66", "64+66", "65", "68", "69-75", "69-71", "72", "73-75", "77-82", "77+79-82", "78", "85", "86-88", "90-93", "94-96", "94-95", "96");

    @Override
    public String totalId() {
        return "01-96";
    }

    @Override
    public List<String> ids() {
        return IDS;
    }
}
