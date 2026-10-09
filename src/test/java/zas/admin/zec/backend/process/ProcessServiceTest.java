package zas.admin.zec.backend.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import zas.admin.zec.backend.persistence.repository.BusinessProcessRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static zas.admin.zec.backend.process.ProcessFixtures.*;

class ProcessServiceTest {

    private ProcessService service;

    @BeforeEach
    void setUp() {
        var repository = mock(BusinessProcessRepository.class);
        ProcessFixtures.stub(repository);
        service = new ProcessService(repository);
    }

    @Test
    @DisplayName("Le JSON est lu : identifiants, nom, état, lien et processus appelés")
    void parsesProcessJson() {
        var process = service.findByBpmnIds(List.of(MAIN_BPMN)).getFirst();

        assertEquals(MAIN_BPANDA, process.bpandaId());
        assertEquals(MAIN_NAME, process.name());
        assertEquals("Brouillon", process.state());
        assertEquals("https://prozesse.example/main", process.bpandaLink());
        assertEquals(List.of(SUB_BPMN), process.calledBpmnIds());
        assertFalse(process.json().contains("\n"), "JSON compact attendu");
    }

    @Test
    @DisplayName("Recherche par identifiants : ordre de la demande conservé, inconnus ignorés")
    void findByIdsKeepsRequestedOrder() {
        var processes = service.findByBpmnIds(List.of(OTHER_BPMN, "inconnu", MAIN_BPMN));

        assertEquals(List.of(OTHER_BPMN, MAIN_BPMN), processes.stream().map(BusinessProcess::bpmnId).toList());
        assertEquals(MAIN_BPMN, service.findByBpandaIds(List.of(MAIN_BPANDA)).getFirst().bpmnId());
        assertTrue(service.findByBpmnIds(List.of()).isEmpty());
    }

    @Test
    @DisplayName("Nom exact, à la casse et aux accents près")
    void findByExactNameIgnoringCaseAndAccents() {
        var match = service.findByName("csc af - verifier les PIECES");

        assertTrue(match.found());
        assertEquals(SUB_BPMN, match.process().bpmnId());
    }

    @Test
    @DisplayName("Nom partiel et pluriel : « demande d'exemple » retrouve un seul processus")
    void findByPartialName() {
        var match = service.findByName("demande d'exemple");

        assertTrue(match.found());
        assertEquals(MAIN_BPMN, match.process().bpmnId());
    }

    @Test
    @DisplayName("Nom ambigu : candidats rendus, aucun processus choisi")
    void ambiguousNameReturnsCandidates() {
        var match = service.findByName("traiter les demandes");

        assertFalse(match.found());
        assertEquals(List.of(MAIN_NAME, OTHER_NAME), match.candidates());
    }

    @Test
    @DisplayName("Nom inconnu ou vide : ni processus ni candidat")
    void unknownName() {
        assertFalse(service.findByName("calcul des rentes de vieillesse").found());
        assertTrue(service.findByName("calcul des rentes de vieillesse").candidates().isEmpty());
        assertFalse(service.findByName("  ").found());
    }

    @Test
    @DisplayName("Sous-processus : callActivity suivies, cycle sans doublon ni processus de départ")
    void resolvesCalledProcessesWithoutCycle() {
        var main = service.findByBpmnIds(List.of(MAIN_BPMN)).getFirst();

        var called = service.resolveCalledProcesses(main);

        assertEquals(List.of(SUB_BPMN), called.stream().map(BusinessProcess::bpmnId).toList());
    }

    @Test
    @DisplayName("Rendu : bloc processus avec nom et état, puis bloc sous-processus")
    void rendersProcessAndSubProcess() {
        var main = service.findByBpmnIds(List.of(MAIN_BPMN)).getFirst();

        String block = service.render(main);

        assertTrue(block.startsWith("<processus nom=\"" + MAIN_NAME + "\" etat=\"Brouillon\">"));
        assertTrue(block.contains(main.json()));
        assertTrue(block.contains("<sous_processus nom=\"" + SUB_NAME + "\" appele_par=\"" + MAIN_NAME + "\">"));
        assertTrue(block.endsWith("</sous_processus>"));
    }

    @Test
    @DisplayName("Documents retrouvés : processus cités par processId ou par nom, sans doublon")
    void findsProcessesReferencedByDocuments() {
        var byUrl = new Document("a", Map.of("outgoing_links", List.of(Map.of("url", "https://x.example?processId=" + MAIN_BPANDA))));
        var byName = new Document("b", Map.of("outgoing_links", List.of("process:" + OTHER_NAME, "process:" + MAIN_NAME)));
        var none = new Document("c", Map.of("title", "example.pdf"));

        var processes = service.findReferencedBy(List.of(byUrl, byName, none));

        assertEquals(List.of(MAIN_BPMN, OTHER_BPMN), processes.stream().map(BusinessProcess::bpmnId).toList());
    }

    @Test
    @DisplayName("Catalogue : un nom par ligne")
    void catalogListsNames() {
        assertEquals(3, service.catalog().lines().count());
        assertTrue(service.catalog().lines().allMatch(line -> line.startsWith("- CSC AF - ")));
    }

    @Test
    @DisplayName("Registre des processus chargés : sans doublon, les plus récents en dernier")
    void loadedProcessesKeepsMostRecent() {
        var loaded = new LoadedProcesses();
        var all = service.findByBpmnIds(List.of(MAIN_BPMN, SUB_BPMN, OTHER_BPMN));

        all.forEach(loaded::add);

        assertFalse(loaded.add(all.getFirst()));
        assertEquals(List.of(SUB_BPMN, OTHER_BPMN), loaded.mostRecent(2).stream().map(BusinessProcess::bpmnId).toList());
    }
}
