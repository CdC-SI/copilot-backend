package zas.admin.zec.backend.actions.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zas.admin.zec.backend.persistence.entity.SourceEntity;
import zas.admin.zec.backend.persistence.entity.WorkspaceEntity;
import zas.admin.zec.backend.persistence.repository.SourceRepository;
import zas.admin.zec.backend.persistence.repository.WorkspaceRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private CatalogService catalogService;

    private static SourceEntity source(String name, String description, String... questions) {
        var entity = new SourceEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setHypotheticalQuestions(questions);
        return entity;
    }

    private static WorkspaceEntity workspace(String name, String description, SourceEntity... sources) {
        var entity = new WorkspaceEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setHypotheticalQuestions(new String[0]);
        entity.setSources(new LinkedHashSet<>(List.of(sources)));
        return entity;
    }

    @Test
    @DisplayName("importCatalog crée, met à jour et détecte les éléments inchangés")
    void importCatalog_upsert() {
        var avs = source("avs", "AVS", "q1");
        var ai = source("ai", "old");
        var existingWorkspace = workspace("general", "Général", avs);
        when(sourceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(avs, ai));
        when(sourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceRepository.findByName("general")).thenReturn(Optional.of(existingWorkspace));
        when(workspaceRepository.findByName("nouveau")).thenReturn(Optional.empty());

        var catalog = new CatalogFile(1,
                List.of(new CatalogFile.SourceEntry("avs", "AVS", List.of("q1")),
                        new CatalogFile.SourceEntry("ai", null, List.of("q2")),
                        new CatalogFile.SourceEntry("apg", "APG", null)),
                List.of(new CatalogFile.WorkspaceEntry("general", "Général", null, List.of("avs")),
                        new CatalogFile.WorkspaceEntry("nouveau", null, List.of("q"), List.of("apg", "ai"))));

        var report = catalogService.importCatalog(catalog);

        assertEquals(List.of("apg"), report.sources().created());
        assertEquals(List.of("ai"), report.sources().updated());
        assertEquals(List.of("avs"), report.sources().unchanged());
        assertEquals(List.of("nouveau"), report.workspaces().created());
        assertEquals(List.of(), report.workspaces().updated());
        assertEquals(List.of("general"), report.workspaces().unchanged());

        assertNull(ai.getDescription());
        assertArrayEquals(new String[]{"q2"}, ai.getHypotheticalQuestions());
        verify(sourceRepository, never()).save(avs);
        verify(workspaceRepository, never()).save(existingWorkspace);

        var captor = ArgumentCaptor.forClass(WorkspaceEntity.class);
        verify(workspaceRepository).save(captor.capture());
        var created = captor.getValue();
        assertEquals("nouveau", created.getName());
        assertEquals(List.of("apg", "ai"), created.getSources().stream().map(SourceEntity::getName).toList());
    }

    @Test
    @DisplayName("importCatalog remplace les sources d'un workspace existant")
    void importCatalog_updatesWorkspaceSources() {
        var avs = source("avs", null);
        var ai = source("ai", null);
        var existing = workspace("general", "Général", avs);
        when(sourceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(avs, ai));
        when(workspaceRepository.findByName("general")).thenReturn(Optional.of(existing));

        var report = catalogService.importCatalog(new CatalogFile(1, null,
                List.of(new CatalogFile.WorkspaceEntry("general", null, null, List.of("ai")))));

        assertEquals(List.of("general"), report.workspaces().updated());
        assertNull(existing.getDescription());
        assertEquals(Set.of(ai), existing.getSources());
        verify(workspaceRepository).save(existing);
    }

    @Test
    @DisplayName("importCatalog rejette tout le fichier si une référence est invalide, sans aucune écriture")
    void importCatalog_invalidReferences() {
        when(sourceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(source("avs", null)));

        var catalog = new CatalogFile(1,
                List.of(new CatalogFile.SourceEntry("ai", null, null),
                        new CatalogFile.SourceEntry("ai", null, null)),
                List.of(new CatalogFile.WorkspaceEntry("w", null, null, List.of("avs", "avs", "inconnue")),
                        new CatalogFile.WorkspaceEntry("w", null, null, List.of())));

        var errors = assertThrows(InvalidCatalogException.class, () -> catalogService.importCatalog(catalog)).getErrors();

        assertEquals(4, errors.size(), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("inconnue")));
        verify(sourceRepository, never()).save(any());
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("exportCatalog retourne toutes les sources et workspaces")
    void exportCatalog() {
        var avs = source("avs", "AVS", "q1");
        var ai = source("ai", null);
        when(sourceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ai, avs));
        when(workspaceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(workspace("general", "Général", avs, ai)));

        var catalog = catalogService.exportCatalog();

        assertEquals(CatalogFile.CURRENT_VERSION, catalog.version());
        assertEquals(List.of("ai", "avs"), catalog.sources().stream().map(CatalogFile.SourceEntry::name).toList());
        assertEquals(List.of("q1"), catalog.sources().get(1).hypotheticalQuestions());
        assertEquals(List.of("ai", "avs"), catalog.workspaces().getFirst().sources());
    }
}
