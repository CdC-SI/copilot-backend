package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import zas.admin.zec.backend.persistence.entity.BusinessProcessEntity;
import zas.admin.zec.backend.persistence.repository.BusinessProcessRepository;
import zas.admin.zec.backend.persistence.repository.BusinessProcessRepository.ProcessNameView;
import zas.admin.zec.backend.process.LoadedProcesses;
import zas.admin.zec.backend.process.ProcessService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessToolTest {

    private static final String ADHESION = "CSC AF - Traiter les demandes d'exemple";
    private static final String RESTITUTION = "CSC AF - Traiter les demandes de restitution";

    private ProcessTool tool;
    private LoadedProcesses loaded;

    @BeforeEach
    void setUp() {
        var repository = mock(BusinessProcessRepository.class);
        var entities = List.of(entity("bpmn-1", ADHESION), entity("bpmn-2", RESTITUTION));
        when(repository.findAllNames()).thenReturn(List.of(view("bpmn-1", ADHESION), view("bpmn-2", RESTITUTION)));
        when(repository.findByBpmnIdIn(anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            return entities.stream().filter(e -> ids.contains(e.getBpmnId())).toList();
        });
        tool = new ProcessTool(new ProcessService(repository));
        loaded = new LoadedProcesses();
    }

    @Test
    @DisplayName("Processus trouvé : bloc <processus> rendu et enregistré comme chargé")
    void returnsProcessBlock() {
        String result = tool.getBusinessProcess("demande d'exemple", context());

        assertTrue(result.startsWith("<processus nom=\"" + ADHESION + "\""));
        assertTrue(loaded.contains("bpmn-1"));
    }

    @Test
    @DisplayName("Processus déjà chargé : pas de second bloc")
    void alreadyLoaded() {
        tool.getBusinessProcess(ADHESION, context());

        String result = tool.getBusinessProcess(ADHESION, context());

        assertFalse(result.contains("<processus"));
        assertTrue(result.contains("déjà présent"));
    }

    @Test
    @DisplayName("Nom ambigu : candidats proposés, rien n'est chargé")
    void ambiguousName() {
        String result = tool.getBusinessProcess("traiter les demandes", context());

        assertTrue(result.contains(ADHESION) && result.contains(RESTITUTION));
        assertTrue(result.contains("Demandez à l'utilisateur"));
        assertTrue(loaded.mostRecent(5).isEmpty());
    }

    @Test
    @DisplayName("Nom inconnu ou fonctionnalité désactivée : message explicite")
    void unknownNameOrDisabled() {
        assertTrue(tool.getBusinessProcess("calcul des rentes", context()).startsWith("Aucun processus"));
        assertTrue(tool.getBusinessProcess(ADHESION, new ToolContext(Map.of())).contains("pas disponible"));
    }

    private ToolContext context() {
        return new ToolContext(Map.of(ToolContextKeys.CTX_LOADED_PROCESSES, loaded, ToolContextKeys.CTX_LANGUAGE, "fr"));
    }

    private static BusinessProcessEntity entity(String bpmnId, String name) {
        var entity = new BusinessProcessEntity();
        entity.setBpmnId(bpmnId);
        entity.setContent("{\"process_name\": \"%s\", \"process_state\": \"Brouillon\", \"nodes\": [], \"arcs\": []}".formatted(name));
        return entity;
    }

    private static ProcessNameView view(String bpmnId, String name) {
        return new ProcessNameView() {
            @Override
            public String getBpmnId() {
                return bpmnId;
            }

            @Override
            public String getProcessName() {
                return name;
            }
        };
    }
}
