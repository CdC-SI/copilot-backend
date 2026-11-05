package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import zas.admin.zec.backend.config.properties.LinkedProcessProperties;
import zas.admin.zec.backend.tools.RAGTool.LinkedSource;
import zas.admin.zec.backend.tools.document.BusinessProcessLookup;
import zas.admin.zec.backend.tools.document.DocumentCollection;
import zas.admin.zec.backend.tools.document.DocumentLookup.LookupRequest;
import zas.admin.zec.backend.tools.document.DocumentLookup.LookupResult;
import zas.admin.zec.backend.tools.document.FilterOp;
import zas.admin.zec.backend.tools.document.MetadataFilterSpec;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Processus liés joints d'office : choix des cibles, accès, taille maximale, relecture après une question de suivi. */
class LinkedProcessAttacherTest {

    private static final String ADHESION = "CSC AF-61-1-100";
    private static final String EXCLUSION = "CSC AF-61-2-408";

    private final BusinessProcessLookup lookup = mock(BusinessProcessLookup.class);

    private LinkedProcessAttacher attacher(boolean enabled, int maxProcesses, int maxChars) {
        return new LinkedProcessAttacher(lookup, new LinkedProcessProperties(enabled, maxProcesses, maxChars));
    }

    static Document process(String id, String processId, String content) {
        return new Document(id, content, Map.of(MetadataFormatter.META_COLLECTION, "business_process",
                "process_id", processId, "process_name", "Processus " + processId));
    }

    private static List<LinkedSource> targets() {
        return List.of(
                new LinkedSource("a1", DocumentCollection.DOCUMENTATION, "doc_id", "AM-12", null),
                new LinkedSource("a1", DocumentCollection.BUSINESS_PROCESS, "process_id", ADHESION, "Adhésion"),
                new LinkedSource("a2", DocumentCollection.BUSINESS_PROCESS, "process_id", EXCLUSION, "Exclusion"));
    }

    private LookupRequest request() {
        var captor = ArgumentCaptor.forClass(LookupRequest.class);
        verify(lookup).find(captor.capture());
        return captor.getValue();
    }

    @Test
    void firstLinkedProcessesAreReadInForceInRelevanceOrder() {
        when(lookup.find(any())).thenReturn(new LookupResult(
                List.of(process("p2", EXCLUSION, "{}"), process("p1", ADHESION, "{\"nodes\":[]}")), false, "notice"));

        var attachment = attacher(true, 2, 20_000).forSearch(targets(), "u1", true, "fr");

        assertEquals(List.of("p1", "p2"), attachment.documents().stream().map(Document::getId).toList());
        assertEquals(List.of("a1", "a2"), attachment.attached().stream().map(LinkedProcessAttacher.Attached::from).toList());
        assertEquals("notice", attachment.notice());
        var request = request();
        assertEquals(List.of(new MetadataFilterSpec("process_id", FilterOp.IN, null, List.of(ADHESION, EXCLUSION))),
                request.filters());
        assertTrue(request.includeContent());
        assertTrue(request.hasInternalAccess());
        assertEquals("fr", request.language());
    }

    @Test
    void onlyMaxProcessesTargetsAreRead() {
        when(lookup.find(any())).thenReturn(new LookupResult(List.of(process("p1", ADHESION, "{}")), false, null));

        attacher(true, 1, 20_000).forSearch(targets(), "u1", true, "fr");

        assertEquals(List.of(ADHESION), request().filters().getFirst().values());
    }

    @Test
    void nothingIsAttachedWithoutInternalAccessWhenDisabledOrWithoutProcessTarget() {
        assertTrue(attacher(true, 1, 20_000).forSearch(targets(), "u1", false, "fr").attached().isEmpty());
        assertTrue(attacher(false, 1, 20_000).forSearch(targets(), "u1", true, "fr").attached().isEmpty());
        assertTrue(attacher(true, 1, 20_000).forSearch(targets().subList(0, 1), "u1", true, "fr").attached().isEmpty());
        assertTrue(attacher(true, 1, 20_000).forFollowUp(List.of("p1"), "u1", false, "fr").attached().isEmpty());
        verifyNoInteractions(lookup);
    }

    @Test
    void processesBeyondTheCharacterLimitAreSkipped() {
        when(lookup.find(any())).thenReturn(new LookupResult(
                List.of(process("p1", ADHESION, "x".repeat(80)), process("p2", EXCLUSION, "y".repeat(30))), false, null));

        var attachment = attacher(true, 2, 100).forSearch(targets(), "u1", true, "fr");

        assertEquals(List.of("p1"), attachment.documents().stream().map(Document::getId).toList());
    }

    @Test
    void lookupFailureAttachesNothing() {
        when(lookup.find(any())).thenThrow(new IllegalStateException("db down"));

        assertTrue(attacher(true, 1, 20_000).forSearch(targets(), "u1", true, "fr").attached().isEmpty());
    }

    @Test
    void followUpRereadsTheVersionConsultedByItsId() {
        when(lookup.find(any())).thenReturn(new LookupResult(List.of(process("p1", ADHESION, "{}")), false, null));

        var attachment = attacher(true, 1, 20_000).forFollowUp(List.of("p1", "p1", "p2"), "u1", true, "fr");

        assertEquals(List.of(new MetadataFilterSpec("id", FilterOp.IN, null, List.of("p1"))), request().filters());
        assertNull(attachment.attached().getFirst().from());
    }

    @Test
    void blockWrapsEachProcessInTheUsualDocumentFormatWithoutInstruction() {
        var document = process("p1", ADHESION, "{\"nodes\":[]}");
        String nl = System.lineSeparator();

        String block = LinkedProcessAttacher.format(new LinkedProcessAttacher.Attachment(
                List.of(new LinkedProcessAttacher.Attached("a1", document)), "Only the latest version."));

        assertEquals("<linked_processes>" + nl
                + "<notice>Only the latest version.</notice>" + nl
                + "<linked_process from=\"a1\">" + nl
                + MetadataFormatter.format(document, true, true) + nl
                + "</linked_process>" + nl
                + "</linked_processes>", block);
        assertTrue(LinkedProcessAttacher.format(new LinkedProcessAttacher.Attachment(
                List.of(new LinkedProcessAttacher.Attached(null, document)), null)).contains("<linked_process>" + nl));
        assertEquals("", LinkedProcessAttacher.format(new LinkedProcessAttacher.Attachment(List.of(), null)));
    }
}
