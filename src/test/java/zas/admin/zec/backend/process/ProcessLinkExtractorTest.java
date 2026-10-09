package zas.admin.zec.backend.process;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessLinkExtractorTest {

    private static final String BPANDA_URL = "https://prozesse.example/processpage/ID-0123456789abcdef0123456789abcdef"
            + "?x=1&processId=ID-0123456789abcdef0123456789abcdef&version=9#main";

    @Test
    @DisplayName("Format actuel : processId extrait de l'URL BPanda d'un lien objet")
    void extractsProcessIdFromObjectLink() {
        var metadata = Map.of("outgoing_links", List.of(
                Map.of("text", "Process \"Traiter un exemple\"— bpanda", "url", BPANDA_URL, "page", 1),
                Map.of("text", "art. 3 LAVS", "url", "https://www.fedlex.example/eli/cc/63#art_3", "page", 1)));

        var refs = ProcessLinkExtractor.extract(metadata);

        assertEquals(Set.of("ID-0123456789abcdef0123456789abcdef"), refs.bpandaIds());
        assertTrue(refs.names().isEmpty());
    }

    @Test
    @DisplayName("Format cible : nom de processus extrait d'une chaîne process:<nom>")
    void extractsNameFromProcessPrefix() {
        var metadata = Map.of("outgoing_links", List.of("process:Traiter un exemple", "url:https://x.example", "pdf:example.pdf"));

        var refs = ProcessLinkExtractor.extract(metadata);

        assertEquals(Set.of("Traiter un exemple"), refs.names());
        assertTrue(refs.bpandaIds().isEmpty());
    }

    @Test
    @DisplayName("Même processus cité deux fois : une seule référence")
    void deduplicatesReferences() {
        var link = Map.of("url", BPANDA_URL);
        var refs = ProcessLinkExtractor.extract(Map.of("outgoing_links", List.of(link, link)));

        assertEquals(1, refs.bpandaIds().size());
    }

    @Test
    @DisplayName("Aucune référence : liens sans processId, liste vide, clé absente, valeurs nulles")
    void emptyWhenNoProcessReference() {
        var withNull = new HashMap<String, Object>();
        withNull.put("outgoing_links", java.util.Arrays.asList(null, Map.of("text", "sans url"), "process:  "));

        assertTrue(ProcessLinkExtractor.extract(Map.of("outgoing_links", List.of(Map.of("url", "https://x.example")))).isEmpty());
        assertTrue(ProcessLinkExtractor.extract(Map.of("outgoing_links", List.of())).isEmpty());
        assertTrue(ProcessLinkExtractor.extract(Map.of("title", "example.pdf")).isEmpty());
        assertTrue(ProcessLinkExtractor.extract(withNull).isEmpty());
        assertTrue(ProcessLinkExtractor.extract(null).isEmpty());
    }
}
