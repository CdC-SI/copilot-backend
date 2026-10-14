package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.tools.document.DocumentCollection;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetadataFormatterTest {

    @Test
    void exposesOnlyAllowListedKeysInFixedOrder() {
        var doc = Document.builder().id("d1").text("Contenu").metadata(Map.of(
                "user_uuid", "u", "organizations", List.of("ZAS"), "title", "Mémento",
                "doc_id", "p1", "chunk_index", 3, "tags", List.of("x"),
                "outgoing_links", List.of(Map.of("type", "business_process", "process_id", "BP-1")))).build();

        String formatted = MetadataFormatter.format(doc, true, true);

        assertTrue(formatted.startsWith("<document id=\"d1\" collection=\"documentation\">"));
        assertTrue(formatted.contains("Contenu"));
        assertTrue(formatted.contains("<metadata>{\"doc_id\":\"p1\",\"chunk_index\":3,\"title\":\"Mémento\",\"outgoing_links\":"));
        assertFalse(formatted.contains("user_uuid"));
        assertFalse(formatted.contains("organizations"));
        assertFalse(formatted.contains("tags"));
    }

    @Test
    void metadataOnlyOmitsContent() {
        var doc = Document.builder().id("p").text("{\"nodes\":[]}").metadata(Map.of(
                MetadataFormatter.META_COLLECTION, DocumentCollection.BUSINESS_PROCESS.key(),
                "process_id", "CSC AF-61-2-408", "process_state", "Brouillon", "lang", "fr")).build();

        String formatted = MetadataFormatter.format(doc, false, true);

        assertTrue(formatted.startsWith("<document id=\"p\" collection=\"business_process\"/>"));
        assertFalse(formatted.contains("nodes"));
        assertTrue(formatted.contains("\"process_state\":\"Brouillon\""));
        assertFalse(formatted.contains(MetadataFormatter.META_COLLECTION));
    }

    @Test
    void consultedSourceRoundTripsAndMapsToSourceTokens() {
        var process = Document.builder().id("p").text("").metadata(Map.of(
                MetadataFormatter.META_COLLECTION, "business_process", "process_id", "BP-1",
                "process_name", "Traiter les décès", "bpanda_link", "https://bpanda/x", "bpanda_version", "2026.03.02")).build();
        var chunk = Document.builder().id("c").text("t").metadata(Map.of(
                "title", "Mémento.pdf", "page_num", "2", "state", "personal.uploads", "doc_id", "D")).build();

        var bp = ConsultedSource.from(process);
        var doc = ConsultedSource.from(chunk);

        assertEquals(bp, ConsultedSource.fromMap(bp.toMap()));
        assertEquals(doc, ConsultedSource.fromMap(doc.toMap()));
        assertTrue(bp.isBusinessProcess());
        assertEquals("<source><id>p</id><url>https://bpanda/x</url><file></file><pn></pn><sub></sub><v>2026.03.02</v>"
                + "<type>BUSINESS_PROCESS</type><t>Traiter les décès</t></source>", bp.toSourceToken().content());
        // Pour la documentation, l'état est transmis dans le champ version (traduit par le frontend).
        assertTrue(doc.toSourceToken().content().contains("<file>Mémento.pdf</file><pn>2</pn><sub></sub><v>personal.uploads</v><type>FILE</type>"));
    }
}
