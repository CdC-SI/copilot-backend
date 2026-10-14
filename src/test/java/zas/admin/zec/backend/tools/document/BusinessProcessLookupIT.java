package zas.admin.zec.backend.tools.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import zas.admin.zec.backend.tools.MetadataFormatter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BusinessProcessLookup} sur une vraie base : accès interne, sélection de la version en vigueur,
 * préférence de langue avec repli, versions explicites.
 *
 * <p>Processus A : v1 expirée (Validé), v2 en vigueur (Brouillon, fr + de), v3 future (Validé), toutes avec le
 * même {@code bpmn_id} et le même {@code bpanda_id} (stables d'une version et d'une traduction à l'autre).
 * Processus B : une version en vigueur (fr). Processus C : traduction allemande en retard (de expirée, fr en
 * vigueur). Dates choisies pour rester stables jusqu'en 2098.</p>
 */
class BusinessProcessLookupIT extends AbstractDocumentLookupIT {

    private static final String A1_FR = "00000000-0000-0000-0000-0000000000a1";
    private static final String A2_FR = "00000000-0000-0000-0000-0000000000a2";
    private static final String A2_DE = "00000000-0000-0000-0000-0000000000a3";
    private static final String A3_FR = "00000000-0000-0000-0000-0000000000a4";
    private static final String B1_FR = "00000000-0000-0000-0000-0000000000b1";
    private static final String C1_DE = "00000000-0000-0000-0000-0000000000c1";
    private static final String C2_FR = "00000000-0000-0000-0000-0000000000c2";

    @Autowired
    private BusinessProcessLookup lookup;

    private void insert(String id, String processId, String name, String bpmnId, String version, String state,
                        String validFrom, String validTo, String lang) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("bpmn_id", bpmnId);
        metadata.put("process_id", processId);
        metadata.put("process_name", name);
        metadata.put("process_state", state);
        metadata.put("purpose", null);
        metadata.put("bpanda_version", version);
        metadata.put("valid_from", validFrom);
        metadata.put("valid_to", validTo);
        metadata.put("bpanda_id", "IT-BPANDA-" + processId);
        metadata.put("bpanda_link", "https://bpanda.example/" + processId);
        metadata.put("lang", lang);
        metadata.put("organizations", List.of("ZAS"));
        String content = json(Map.of(
                "nodes", List.of(Map.of("id", "n1", "kind", "Task", "label", name + " " + version)),
                "arcs", List.of()));
        jdbcTemplate.update("INSERT INTO business_process (id, content, metadata) VALUES (?::uuid, ?::jsonb, ?::jsonb)",
                id, content, json(metadata));
    }

    @BeforeEach
    void data() {
        insert(A1_FR, "IT-A", "IT Traiter les décès", "IT-BPMN-A", "2020.01.01", "Validé",
                "2020-01-01T00:00", "2021-12-31T23:59", "fr");
        insert(A2_FR, "IT-A", "IT Traiter les décès", "IT-BPMN-A", "2022.01.01", "Brouillon",
                "2022-01-01T00:00", "2098-12-31T23:59", "fr");
        insert(A2_DE, "IT-A", "IT Todesfälle bearbeiten", "IT-BPMN-A", "2022.01.01", "Brouillon",
                "2022-01-01T00:00", "2098-12-31T23:59", "de");
        insert(A3_FR, "IT-A", "IT Traiter les décès", "IT-BPMN-A", "2099.01.01", "Validé",
                "2099-01-01T00:00", null, "fr");
        insert(B1_FR, "IT-B", "IT Retaxer une période", "IT-BPMN-B", "2022.06.01", "Validé",
                "2022-06-01T00:00", "2098-12-31T23:59", "fr");
        insert(C1_DE, "IT-C", "IT Konto abschliessen", "IT-BPMN-C", "2020.01.01", "Validé",
                "2020-01-01T00:00", "2021-12-31T23:59", "de");
        insert(C2_FR, "IT-C", "IT Clôturer un compte", "IT-BPMN-C", "2022.01.01", "Validé",
                "2022-01-01T00:00", "2098-12-31T23:59", "fr");
    }

    @Test
    void usersWithoutInternalAccessGetNothing() {
        var result = lookup.find(request(List.of(eq("process_id", "IT-A")), "u", false, "fr"));

        assertTrue(result.documents().isEmpty());
        assertEquals(BusinessProcessLookup.NO_ACCESS_NOTICE, result.notice());
    }

    @Test
    void returnsTheVersionInForceInTheUserLanguage() {
        var fr = lookup.find(request(List.of(eq("process_id", "IT-A")), "u", true, "fr"));
        var de = lookup.find(request(List.of(eq("process_id", "IT-A")), "u", true, "de"));

        assertEquals(List.of(A2_FR), ids(fr));
        assertEquals(List.of(A2_DE), ids(de));
        assertEquals(BusinessProcessLookup.LATEST_ONLY_NOTICE, fr.notice());
    }

    @Test
    void returnsOneTranslationWhenNoneMatchesTheUserLanguage() {
        var result = lookup.find(request(List.of(eq("process_id", "IT-A")), "u", true, "it"));

        assertEquals(List.of(A2_FR), ids(result));
        assertTrue(result.notice().contains(BusinessProcessLookup.OTHER_LANGUAGE_NOTICE.formatted("it")));
    }

    @Test
    void versionInForceWinsOverTheUserLanguage() {
        var result = lookup.find(request(List.of(eq("process_id", "IT-C")), "u", true, "de"));

        assertEquals(List.of(C2_FR), ids(result));
        assertTrue(result.notice().contains(BusinessProcessLookup.OTHER_LANGUAGE_NOTICE.formatted("de")));
    }

    @Test
    void explicitVersionPrefersTheUserLanguageAmongTranslations() {
        var fr = lookup.find(request(List.of(eq("process_id", "IT-A"), eq("bpanda_version", "2022.01.01")), "u", true, "fr"));
        var de = lookup.find(request(List.of(eq("process_id", "IT-A"), eq("bpanda_version", "2022.01.01")), "u", true, "de"));

        assertEquals(List.of(A2_FR), ids(fr));
        assertEquals(List.of(A2_DE), ids(de));
    }

    @Test
    void explicitLanguageFilterOverridesTheUserLanguage() {
        var result = lookup.find(request(List.of(eq("process_id", "IT-A"), eq("lang", "de")), "u", true, "fr"));

        assertEquals(List.of(A2_DE), ids(result));
    }

    @Test
    void stableIdentifiersReturnTheVersionInForce() {
        var byBpmnId = lookup.find(request(List.of(eq("bpmn_id", "IT-BPMN-A")), "u", true, "fr"));
        var byBpmnIdDe = lookup.find(request(List.of(eq("bpmn_id", "IT-BPMN-A")), "u", true, "de"));
        var byBpandaId = lookup.find(request(List.of(eq("bpanda_id", "IT-BPANDA-IT-A")), "u", true, "fr"));

        assertEquals(List.of(A2_FR), ids(byBpmnId));
        assertEquals(List.of(A2_DE), ids(byBpmnIdDe));
        assertEquals(List.of(A2_FR), ids(byBpandaId));
        assertEquals(BusinessProcessLookup.LATEST_ONLY_NOTICE, byBpmnId.notice());
    }

    @Test
    void versionCanBeSelectedWithinAStableBpmnId() {
        var result = lookup.find(request(List.of(eq("bpmn_id", "IT-BPMN-A"), eq("bpanda_version", "2099.01.01")), "u", true, "fr"));

        assertEquals(List.of(A3_FR), ids(result));
        assertNull(result.notice());
    }

    @Test
    void explicitVersionReturnsThatVersion() {
        var result = lookup.find(request(List.of(eq("process_id", "IT-A"), eq("bpanda_version", "2020.01.01")), "u", true, "fr"));

        assertEquals(List.of(A1_FR), ids(result));
        assertNull(result.notice());
    }

    @Test
    void validityRangeReturnsEveryMatchingVersion() {
        var result = lookup.find(request(List.of(eq("process_id", "IT-A"), op("valid_from", FilterOp.GTE, "2021-01-01T00:00")), "u", true, "fr"));

        assertEquals(List.of(A3_FR, A2_FR), ids(result));
    }

    @Test
    void filtersApplyBeforePickingTheLatestVersion() {
        // Aucune version "Validé" en vigueur : la plus récente (future) est retournée.
        var result = lookup.find(request(List.of(eq("process_id", "IT-A"), eq("process_state", "Validé")), "u", true, "fr"));

        assertEquals(List.of(A3_FR), ids(result));
    }

    @Test
    void nameSearchReturnsOneVersionPerProcessSortedByName() {
        var result = lookup.find(request(List.of(op("process_name", FilterOp.CONTAINS, "it ")), "u", true, "fr"));

        assertEquals(List.of(C2_FR, B1_FR, A2_FR), ids(result));
    }

    @Test
    void limitTruncatesAndFlagsMoreResults() {
        var result = lookup.find(new DocumentLookup.LookupRequest(
                List.of(op("process_name", FilterOp.CONTAINS, "it ")), 1, "u", true, "fr", true));

        assertEquals(List.of(C2_FR), ids(result));
        assertTrue(result.truncated());
    }

    @Test
    void metadataOnlySkipsContentAndDropsNullValues() {
        var result = lookup.find(new DocumentLookup.LookupRequest(
                List.of(eq("process_id", "IT-B")), 5, "u", true, "fr", false));

        var document = result.documents().getFirst();
        assertEquals("", document.getText());
        assertFalse(document.getMetadata().containsKey("purpose"));
        assertEquals(DocumentCollection.BUSINESS_PROCESS, MetadataFormatter.collectionOf(document));
        assertTrue(MetadataFormatter.metadataJson(document).contains("\"bpanda_version\":\"2022.06.01\""));
    }

    @Test
    void contentIsTheStoredProcessDefinition() {
        var result = lookup.find(request(List.of(eq("process_id", "IT-B")), "u", true, "fr"));

        assertTrue(result.documents().getFirst().getText().contains("IT Retaxer une période 2022.06.01"));
    }

    @Test
    void unknownKeysAreRejected() {
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class,
                () -> lookup.find(request(List.of(eq("organizations", "ZAS")), "u", true, "fr")));
    }
}
