package zas.admin.zec.backend.actions.workspace;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zas.admin.zec.backend.config.properties.WorkspaceProperties;
import zas.admin.zec.backend.persistence.entity.SourceEntity;
import zas.admin.zec.backend.persistence.entity.WorkspaceEntity;
import zas.admin.zec.backend.persistence.repository.SourceRepository;
import zas.admin.zec.backend.persistence.repository.WorkspaceRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final WorkspaceProperties properties = new WorkspaceProperties();
    private final WorkspaceService service = new WorkspaceService(workspaceRepository, sourceRepository, properties);

    @Test
    void getAll_mapsEntitiesWithSortedSourceNames() {
        var entity = workspace("AVS", source("zeta"), source("alpha"));
        entity.setHypotheticalQuestions(new String[]{"Q1"});
        when(workspaceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(entity));

        var result = service.getAll();

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.name()).isEqualTo("AVS");
            assertThat(dto.sources()).containsExactly("alpha", "zeta");
            assertThat(dto.hypotheticalQuestions()).containsExactly("Q1");
            assertThat(dto.createdAt()).isNotNull();
        });
    }

    @Test
    void getByName_withoutQuestions_returnsEmptyList() {
        when(workspaceRepository.findByName("AVS")).thenReturn(Optional.of(workspace("AVS")));

        assertThat(service.getByName("AVS").hypotheticalQuestions()).isEmpty();
    }

    @Test
    void getByName_unknown_throwsNotFound() {
        when(workspaceRepository.findByName("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByName("X"))
                .isInstanceOf(WorkspaceNotFoundException.class)
                .hasMessageContaining("X");
    }

    @Test
    void create_resolvesSourcesAndSaves() {
        when(workspaceRepository.existsByName("AVS")).thenReturn(false);
        when(sourceRepository.findByName("s1")).thenReturn(Optional.of(source("s1")));
        when(workspaceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var dto = service.create(new CreateWorkspaceRequest("AVS", "Desc", List.of("Q"), List.of("s1")));

        var captor = ArgumentCaptor.forClass(WorkspaceEntity.class);
        verify(workspaceRepository).save(captor.capture());
        assertThat(captor.getValue().getSources()).extracting(SourceEntity::getName).containsExactly("s1");
        assertThat(captor.getValue().getHypotheticalQuestions()).containsExactly("Q");
        assertThat(dto.description()).isEqualTo("Desc");
        assertThat(dto.sources()).containsExactly("s1");
    }

    @Test
    void create_withoutListsStoresEmptyValues() {
        when(workspaceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var dto = service.create(new CreateWorkspaceRequest("AVS", null, null, null));

        assertThat(dto.sources()).isEmpty();
        assertThat(dto.hypotheticalQuestions()).isEmpty();
    }

    @Test
    void create_existingName_throwsConflict() {
        when(workspaceRepository.existsByName("AVS")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateWorkspaceRequest("AVS", null, null, null)))
                .isInstanceOf(WorkspaceAlreadyExistsException.class);
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void create_unknownSource_throwsWithoutSaving() {
        when(sourceRepository.findByName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateWorkspaceRequest("AVS", null, null, List.of("ghost"))))
                .isInstanceOf(UnknownSourceException.class)
                .hasMessageContaining("ghost");
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void update_replacesDescriptionQuestionsAndSources() {
        var entity = workspace("AVS", source("old"));
        when(workspaceRepository.findByName("AVS")).thenReturn(Optional.of(entity));
        when(sourceRepository.findByName("new")).thenReturn(Optional.of(source("new")));
        when(workspaceRepository.save(entity)).thenReturn(entity);

        var dto = service.update("AVS", new UpdateWorkspaceRequest("Nouvelle", List.of("Q2"), List.of("new")));

        assertThat(dto.sources()).containsExactly("new");
        assertThat(dto.description()).isEqualTo("Nouvelle");
        assertThat(entity.getHypotheticalQuestions()).containsExactly("Q2");
    }

    @Test
    void update_unknownWorkspace_throws() {
        when(workspaceRepository.findByName("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("X", new UpdateWorkspaceRequest(null, null, null)))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void delete_removesExistingWorkspace() {
        var entity = workspace("AVS");
        when(workspaceRepository.findByName("AVS")).thenReturn(Optional.of(entity));

        service.delete("AVS");

        verify(workspaceRepository).delete(entity);
    }

    @Test
    void delete_unknownWorkspace_throws() {
        when(workspaceRepository.findByName("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("X")).isInstanceOf(WorkspaceNotFoundException.class);
        verify(workspaceRepository, never()).delete(any());
    }

    @Test
    void resolveSources_blankWorkspace_returnsNothingWithoutQuery() {
        assertThat(service.resolveSources(null)).isEmpty();
        assertThat(service.resolveSources(" ")).isEmpty();
        verify(workspaceRepository, never()).findSourceNamesByWorkspaceName(any());
    }

    @Test
    void resolveSources_delegatesToRepository() {
        when(workspaceRepository.findSourceNamesByWorkspaceName("AVS")).thenReturn(List.of("s1", "s2"));

        assertThat(service.resolveSources("AVS")).containsExactly("s1", "s2");
    }

    @Test
    void getAllNames_andDefaultWorkspace() {
        when(workspaceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(workspace("AI"), workspace("AVS")));
        properties.setDefaultWorkspace("AVS");

        assertThat(service.getAllNames()).containsExactly("AI", "AVS");
        assertThat(service.getDefaultWorkspace()).isEqualTo("AVS");
    }

    private static WorkspaceEntity workspace(String name, SourceEntity... sources) {
        var entity = new WorkspaceEntity();
        entity.setId(1L);
        entity.setName(name);
        entity.setSources(new LinkedHashSet<>(List.of(sources)));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static SourceEntity source(String name) {
        var entity = new SourceEntity();
        entity.setName(name);
        return entity;
    }
}
