package zas.admin.zec.backend.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CitationStreamFilterTest {

    private static ConsultedSource chunk(String id, String title, String page) {
        return new ConsultedSource(id, "documentation", null, title, null, page, null, null, null, "D-" + id, "0",
                List.of());
    }

    /** Extraits lus pendant le tour, dans l'ordre : ref 1, 2, 3… */
    private static CitationStreamFilter filter(ConsultedSource... read) {
        CitationRefs refs = new CitationRefs();
        Map<String, ConsultedSource> byId = new java.util.HashMap<>();
        for (ConsultedSource source : read) {
            refs.refFor(source.id());
            byId.put(source.id(), source);
        }
        return new CitationStreamFilter(refs, ref -> refs.idFor(ref).map(byId::get));
    }

    private static String stream(CitationStreamFilter filter, String... chunks) {
        StringBuilder out = new StringBuilder();
        for (String chunk : chunks) {
            out.append(filter.accept(chunk));
        }
        return out.append(filter.flush()).toString();
    }

    private static final ConsultedSource A = chunk("a", "Mémento 1.01", "3");
    private static final ConsultedSource B = chunk("b", "Directive", "12");
    private static final ConsultedSource C = chunk("c", "Loi", "1");

    @Test
    void refsAreRenumberedInOrderOfFirstCitation() {
        var filter = filter(A, B, C);

        String text = stream(filter, "Le délai est de 30 jours [3]. La rente [1] dépend de la durée [3].");

        assertEquals("Le délai est de 30 jours [^1]. La rente [^2] dépend de la durée [^1].", text);
        var citations = filter.citations();
        assertEquals(List.of(1, 2), citations.stream().map(CitationStreamFilter.Citation::number).toList());
        assertEquals(List.of("c", "a"), citations.stream().map(c -> c.source().id()).toList());
        assertEquals(3, citations.getFirst().ref());
    }

    @Test
    void markersSplitAcrossChunksAreResolved() {
        var filter = filter(A, B);

        assertEquals("Texte ", filter.accept("Texte ["));
        assertEquals("", filter.accept("^"));
        assertEquals("[^1] suite", filter.accept("2] suite"));
        assertEquals("", filter.flush());
    }

    @Test
    void groupedAndConsecutiveRefsAreSplitIntoCanonicalMarkers() {
        var filter = filter(A, B, C);

        assertEquals("Voir [^1][^2] et [^3][^1].", stream(filter, "Voir [1, 2] et [3][1].", ""));
        assertEquals("Voir [^1][^2].", stream(filter(A, B), "Voir [1; ^2]."));
    }

    @Test
    void extractsOfTheSameDisplayedSourceShareTheirNumber() {
        var sameSource = chunk("a2", "Mémento 1.01", "3");
        var filter = filter(A, sameSource);

        assertEquals("x [^1] y [^1]", stream(filter, "x [1] y [2]"));
        assertEquals("x [^1]", stream(filter(A, sameSource), "x [1][2]"));
        assertEquals(1, filter.citations().size());
    }

    @Test
    void unknownRefsAreRemoved() {
        var filter = filter(A);

        assertEquals("Texte . Fin [^1].", stream(filter, "Texte [7]. Fin [1, 9]."));
        assertEquals(2, filter.unknownRefs());
    }

    @Test
    void textIsUnchangedWhenNoExtractWasRead() {
        var filter = filter();

        assertEquals("Le tableau [1, 2] reste tel quel [3].", stream(filter, "Le tableau [1, 2] reste ", "tel quel [3]."));
        assertTrue(filter.citations().isEmpty());
    }

    @Test
    void codeIsNeverRewritten() {
        var filter = filter(A);

        String text = stream(filter, "Code `a[1]` puis [1].\n```java\nint[] t = x[1];\n", "```\nFin [1]");

        assertEquals("Code `a[1]` puis [^1].\n```java\nint[] t = x[1];\n```\nFin [^1]", text);
    }

    @Test
    void anUnclosedInlineCodeStopsAtTheEndOfTheLine() {
        assertEquals("Un ` isolé\nVoir [^1]", stream(filter(A), "Un ` isolé\nVoir [1]"));
    }

    @Test
    void bracketsThatAreNotMarkersPassThrough() {
        var filter = filter(A);

        String text = stream(filter, "[lien](https://x) [2024] [1a] [^note] [ 1] [", "1");

        assertEquals("[lien](https://x) [2024] [1a] [^note] [ 1] [1", text);
        assertTrue(filter.citations().isEmpty());
    }

    @Test
    void newCitationsAreDrainedOnce() {
        var filter = filter(A, B);

        filter.accept("a [2] b [1] c [2]");
        assertEquals(List.of("b", "a"), filter.drainNewCitations().stream().map(c -> c.source().id()).toList());
        assertTrue(filter.drainNewCitations().isEmpty());
        filter.accept(" d [1]");
        assertTrue(filter.drainNewCitations().isEmpty());
    }

    @Test
    void markerEndReportsIncompleteInput() {
        List<Integer> refs = new ArrayList<>();
        assertEquals(-2, CitationStreamFilter.markerEnd("[1, ", 0, refs));
        assertEquals(-1, CitationStreamFilter.markerEnd("[12345", 0, new ArrayList<>()));
        assertEquals(5, CitationStreamFilter.markerEnd("[^12]x", 0, refs));
    }

    @Test
    void stripMarkersRemovesCanonicalMarkersOnly() {
        assertEquals("Texte. Suite [1].", CitationStreamFilter.stripMarkers("Texte[^1][^2]. Suite [1]."));
        assertNull(CitationStreamFilter.stripMarkers(null));
    }

    @Test
    void stripMarkersKeepsCode() {
        String text = "Regex `[^1]` et ``a`[^2]`` [^3]\n```\nx = \"[^4]\"\n~~~\n````\nFin [^5] `ouvert [^6]\n";

        assertEquals("Regex `[^1]` et ``a`[^2]``\n```\nx = \"[^4]\"\n~~~\n````\nFin `ouvert\n",
                CitationStreamFilter.stripMarkers(text));
    }

    @Test
    void fencesCloseOnlyOnAMatchingLine() {
        var filter = filter(A);

        String text = stream(filter, "````md\n```\nx[1]\n~~~\ny = \"```\" + a[1]\n", "````\nVoir [1]\n~~~\nz[1]\n~~~ \nFin [1]");

        assertEquals("````md\n```\nx[1]\n~~~\ny = \"```\" + a[1]\n````\nVoir [^1]\n~~~\nz[1]\n~~~ \nFin [^1]", text);
    }

    @Test
    void fenceStateIsKeptAcrossChunksSplitInsideTheFence() {
        var filter = filter(A);

        String text = stream(filter, "``", "`\na[1]\n`", "``", "\n[1]");

        assertEquals("```\na[1]\n```\n[^1]", text);
    }

    @Test
    void markdownLinkAndImageLabelsAreNotCitations() {
        var filter = filter(A, B);

        String text = stream(filter, "Voir [2](https://x/doc) et ![1](img.png) puis [1", "]", "(https://y) [2]\n[1]: https://z\nok [1]: oui");

        assertEquals("Voir [2](https://x/doc) et ![1](img.png) puis [1](https://y) [^1]\n[1]: https://z\nok [^2]: oui", text);
        assertEquals(2, filter.citations().size());
        assertEquals(0, filter.unknownRefs());
    }

    @Test
    void refsAreStablePerId() {
        var refs = new CitationRefs();
        assertTrue(refs.isEmpty());
        assertEquals(1, refs.refFor("a"));
        assertEquals(2, refs.refFor("b"));
        assertEquals(1, refs.refFor("a"));
        assertEquals(Optional.of("b"), refs.idFor(2));
        assertEquals(Optional.empty(), refs.idFor(3));
        assertEquals(List.of(1, 2), List.copyOf(refs.table().keySet()));
    }
}
