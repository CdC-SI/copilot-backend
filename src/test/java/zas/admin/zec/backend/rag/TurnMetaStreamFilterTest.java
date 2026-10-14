package zas.admin.zec.backend.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TurnMetaStreamFilterTest {

    private static String run(TurnMetaStreamFilter filter, String... chunks) {
        var out = new StringBuilder();
        for (String c : chunks) {
            out.append(filter.accept(c));
        }
        return out.append(filter.flush()).toString();
    }

    @Test
    void stripsTagSplitAcrossChunksAndParsesIt() {
        var filter = new TurnMetaStreamFilter();
        String text = run(filter, "La rente est ", "versée.\n<tu", "rn_meta status=\"ans", "wer\" reuse=\"a1, b2\"/>");

        assertEquals("La rente est versée.\n", text);
        var meta = filter.meta().orElseThrow();
        assertEquals(TurnStatus.ANSWER, meta.status());
        assertEquals(List.of("a1", "b2"), meta.reuseIds());
    }

    @Test
    void streamsTextWithoutHoldingUnrelatedAngleBrackets() {
        var filter = new TurnMetaStreamFilter();
        assertEquals("a < b ", filter.accept("a < b "));
        assertEquals("<table>", filter.accept("<table>"));
        // Un suffixe ambigu est retenu puis relâché s'il ne forme pas la balise.
        assertEquals("x ", filter.accept("x <tu"));
        assertEquals("<tutu", filter.accept("tu"));
        assertEquals("", filter.flush());
        assertTrue(filter.meta().isEmpty());
    }

    @Test
    void toleratesAttributeOrderQuotesAndClosingTag() {
        var filter = new TurnMetaStreamFilter();
        String text = run(filter, "Pouvez-vous préciser ?<turn_meta reuse='' status='followup'></turn_meta>");
        assertEquals("Pouvez-vous préciser ?", text);
        assertEquals(TurnStatus.FOLLOWUP, filter.meta().orElseThrow().status());
        assertEquals(List.of(), filter.meta().orElseThrow().reuseIds());
    }

    @Test
    void unknownStatusAndUnclosedTagAreHandled() {
        var filter = new TurnMetaStreamFilter();
        String text = run(filter, "Réponse.<turn_meta status=\"maybe\"");
        assertEquals("Réponse.", text);
        assertEquals(TurnStatus.UNKNOWN, filter.meta().orElseThrow().status());
    }

    @Test
    void filtersInvalidReuseIds() {
        var filter = new TurnMetaStreamFilter();
        run(filter, "<turn_meta status=\"answer\" reuse=\"ok-1,bad id;DROP,\"/>");
        assertEquals(List.of("ok-1", "bad"), filter.meta().orElseThrow().reuseIds());
    }

    @Test
    void absentTagLeavesTextUntouched() {
        var filter = new TurnMetaStreamFilter();
        assertEquals("Bonjour <b>", run(filter, "Bonjour ", "<b>"));
        assertTrue(filter.meta().isEmpty());
    }
}
