package zas.admin.zec.backend.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Valeurs par défaut de {@link TurnTrace}, et sérialisation vers / depuis {@code chat_history.consulted_sources}. */
class TurnTraceTest {

    private static final ConsultedSource CHUNK = new ConsultedSource("a", "documentation", null, "Mémento",
            "https://x/memento", "3", null, null, null, "D", "0", List.of());

    @Test
    void nullComponentsAreReplacedByEmptyValues() {
        var trace = new TurnTrace(null, null, null, null, null, null, null);

        assertEquals(TurnStatus.UNKNOWN, trace.status());
        assertTrue(trace.sources().isEmpty());
        assertTrue(trace.used().isEmpty());
        assertTrue(trace.toolCalls().isEmpty());
        assertNull(trace.usage());
        assertTrue(trace.prompts().isEmpty());
        assertTrue(trace.citations().isEmpty());
        assertEquals(Map.of("turn_status", TurnStatus.UNKNOWN.key(), "sources", List.of()), trace.toMap());
    }

    @Test
    void componentsAreDefensivelyCopiedAndPromptsSorted() {
        var sources = new ArrayList<>(List.of(CHUNK));
        var prompts = new HashMap<>(Map.of("z/fr", "2", "a/fr", "1"));
        var trace = new TurnTrace(TurnStatus.ANSWER, sources, null, null, null, prompts);

        sources.clear();
        prompts.clear();

        assertEquals(List.of(CHUNK), trace.sources());
        assertEquals(List.of("a/fr", "z/fr"), List.copyOf(trace.prompts().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> trace.prompts().put("b/fr", "3"));
    }

    @Test
    void fullTraceSurvivesTheRoundTrip() {
        var trace = new TurnTrace(TurnStatus.ANSWER, List.of(CHUNK),
                new UsedDocuments(List.of("a", "x"), List.of("a"), List.of(), List.of("x")), List.of(), null,
                Map.of("agentic-system/fr", "abc"),
                new TurnCitations(Map.of(1, "a", 2, "b"), List.of(new TurnCitations.Cited(1, 1, "a"))));

        Map<String, Object> map = trace.toMap();
        assertEquals(Map.of("1", "a", "2", "b"), map.get("citation_refs"));
        assertEquals(List.of(Map.of("n", 1, "ref", 1, "id", "a")), map.get("citations"));
        assertEquals(List.of("a", "x"), map.get("used"));

        var read = TurnTrace.fromMap(map);
        assertEquals(trace.status(), read.status());
        assertEquals(trace.used(), read.used());
        assertEquals(trace.prompts(), read.prompts());
        assertEquals(trace.citations(), read.citations());
        assertEquals(List.of("a"), read.sources().stream().map(ConsultedSource::id).toList());
    }

    @Test
    void missingOrEmptyMapGivesAnEmptyTrace() {
        assertEquals(TurnTrace.empty(), TurnTrace.fromMap(null));
        assertEquals(TurnTrace.empty(), TurnTrace.fromMap(Map.of()));
    }

    /** Données historiques ou altérées : les entrées invalides sont ignorées, sans exception. */
    @Test
    void invalidEntriesAreIgnoredWhenReading() {
        Map<String, Object> prompts = new HashMap<>();
        prompts.put("agentic-system/fr", 12);
        prompts.put("sans-version", null);
        Map<Object, Object> refs = new HashMap<>();
        refs.put("1", "a");
        refs.put("deux", "b");
        refs.put(3, null);
        Map<String, Object> cited = new HashMap<>();
        cited.put("n", "1");
        cited.put("ref", 1L);
        cited.put("id", "a");
        Map<String, Object> map = new HashMap<>();
        map.put("turn_status", 42);
        map.put("sources", Arrays.asList("pas une source", null));
        map.put("used", Arrays.asList("a", null, 7));
        map.put("used_current", "pas une liste");
        map.put("prompts", prompts);
        map.put("citation_refs", refs);
        map.put("citations", List.of(cited, Map.of("n", 2), "invalide"));

        var read = TurnTrace.fromMap(map);

        assertEquals(TurnStatus.UNKNOWN, read.status());
        assertTrue(read.sources().isEmpty());
        assertEquals(List.of("a", "7"), read.used().declared());
        assertTrue(read.used().current().isEmpty());
        assertEquals(Map.of("agentic-system/fr", "12"), read.prompts());
        assertEquals(Map.of(1, "a"), read.citations().refs());
        assertEquals(List.of(new TurnCitations.Cited(1, 1, "a")), read.citations().cited());
    }

    @Test
    void withersKeepTheOtherComponents() {
        var usage = new TurnUsage(List.of(), List.of());
        var trace = new TurnTrace(TurnStatus.ANSWER, List.of(CHUNK))
                .withPrompts(Map.of("p/fr", "1"))
                .withUsage(usage)
                .withCitations(new TurnCitations(Map.of(1, "a"), List.of()))
                .withToolCalls(List.of());

        assertEquals(TurnStatus.ANSWER, trace.status());
        assertEquals(List.of(CHUNK), trace.sources());
        assertSame(usage, trace.usage());
        assertEquals(Map.of("p/fr", "1"), trace.prompts());
        assertEquals(Map.of(1, "a"), trace.citations().refs());
        assertFalse(trace.toMap().containsKey("citations"));
        assertTrue(trace.toolCallsAsMaps().isEmpty());
    }
}
