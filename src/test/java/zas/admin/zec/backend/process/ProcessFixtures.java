package zas.admin.zec.backend.process;

import zas.admin.zec.backend.persistence.entity.BusinessProcessEntity;
import zas.admin.zec.backend.persistence.repository.BusinessProcessRepository;
import zas.admin.zec.backend.persistence.repository.BusinessProcessRepository.ProcessNameView;

import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/** Trois processus fictifs : « exemple » appelle « vérification » ; « restitution » rend « traiter les demandes » ambigu. */
final class ProcessFixtures {

    static final String MAIN_BPMN = "bpmn-main";
    static final String SUB_BPMN = "bpmn-sub";
    static final String OTHER_BPMN = "bpmn-other";
    static final String MAIN_BPANDA = "ID-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String MAIN_NAME = "CSC AF - Traiter les demandes d'exemple";
    static final String SUB_NAME = "CSC AF - Vérifier les pièces";
    static final String OTHER_NAME = "CSC AF - Traiter les demandes de restitution";

    static final List<BusinessProcessEntity> ALL = List.of(
            entity(MAIN_BPMN, """
                    {"process_name": "%s", "process_state": "Brouillon", "bpanda_id": "%s",
                     "bpanda_link": "https://prozesse.example/main", "nodes": [
                       {"id": "n1", "kind": "Task", "label": "Réceptionner la demande", "called_bpmn_id": null},
                       {"id": "n2", "kind": "CallActivity", "label": "Vérifier", "called_bpmn_id": "%s"}],
                     "arcs": [{"from": "n1", "to": "n2", "label": null}]}
                    """.formatted(MAIN_NAME, MAIN_BPANDA, SUB_BPMN)),
            entity(SUB_BPMN, """
                    {"process_name": "%s", "process_state": "Brouillon", "bpanda_id": "ID-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                     "nodes": [{"id": "s1", "kind": "CallActivity", "label": "Retour", "called_bpmn_id": "%s"}], "arcs": []}
                    """.formatted(SUB_NAME, MAIN_BPMN)),
            entity(OTHER_BPMN, """
                    {"process_name": "%s", "process_state": "Publié", "bpanda_id": null, "nodes": [], "arcs": []}
                    """.formatted(OTHER_NAME)));

    private ProcessFixtures() {}

    static void stub(BusinessProcessRepository repository) {
        when(repository.findAllNames()).thenAnswer(inv -> ALL.stream()
                .map(e -> (ProcessNameView) new View(e.getBpmnId(), ProcessService.toBusinessProcess(e).name()))
                .toList());
        when(repository.findByBpmnIdIn(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return ALL.stream().filter(e -> ids.contains(e.getBpmnId())).toList();
        });
        when(repository.findByBpandaIdIn(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return ALL.stream()
                    .map(e -> java.util.Map.entry(e, java.util.Objects.toString(ProcessService.toBusinessProcess(e).bpandaId(), "")))
                    .filter(pair -> ids.contains(pair.getValue()))
                    .map(java.util.Map.Entry::getKey)
                    .toList();
        });
    }

    private static BusinessProcessEntity entity(String bpmnId, String content) {
        var entity = new BusinessProcessEntity();
        entity.setBpmnId(bpmnId);
        entity.setContent(content);
        return entity;
    }

    private record View(String bpmnId, String processName) implements ProcessNameView {
        @Override
        public String getBpmnId() {
            return bpmnId;
        }

        @Override
        public String getProcessName() {
            return processName;
        }
    }
}
