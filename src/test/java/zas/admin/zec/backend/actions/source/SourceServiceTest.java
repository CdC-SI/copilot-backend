package zas.admin.zec.backend.actions.source;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.persistence.entity.SourceEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.SourceRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceServiceTest {

    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final TempSourceDocumentRepository tempRepository = mock(TempSourceDocumentRepository.class);
    private final UserService userService = mock(UserService.class);
    private final SourceService service = new SourceService(sourceRepository, documentRepository, tempRepository, userService);

    @Test
    void getAll_listsSourcesWithoutContents() {
        var entity = source("rh");
        entity.setHypotheticalQuestions(new String[]{"Vacances ?"});
        when(sourceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(entity, source("avs")));

        var result = service.getAll();

        assertThat(result).extracting(SourceDto::name).containsExactly("rh", "avs");
        assertThat(result.getFirst().hypotheticalQuestions()).containsExactly("Vacances ?");
        assertThat(result.get(1).hypotheticalQuestions()).isEmpty();
        assertThat(result).allSatisfy(dto -> assertThat(dto.contents()).isEmpty());
        verify(documentRepository, never()).findDistinctContentsBySourceAndUser(any(), any());
    }

    @Test
    void getByName_returnsContentsVisibleToTheUser() {
        when(sourceRepository.findByName("rh")).thenReturn(Optional.of(source("rh")));
        when(userService.getUuid("jdoe")).thenReturn("uuid-1");
        when(documentRepository.findDistinctContentsBySourceAndUser("rh", "uuid-1"))
                .thenReturn(List.of(content("Règlement", "https://rh/reglement")));

        var dto = service.getByName("rh", "jdoe");

        assertThat(dto.contents()).containsExactly(new SourceContentDto("Règlement", "https://rh/reglement"));
    }

    @Test
    void getByName_unknown_throwsNotFound() {
        when(sourceRepository.findByName("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByName("x", "jdoe")).isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void create_savesNewSource() {
        when(sourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var dto = service.create(new CreateSourceRequest("rh", "Ressources humaines", List.of("Q1", "Q2")));

        var captor = ArgumentCaptor.forClass(SourceEntity.class);
        verify(sourceRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("rh");
        assertThat(captor.getValue().getHypotheticalQuestions()).containsExactly("Q1", "Q2");
        assertThat(dto.description()).isEqualTo("Ressources humaines");
        assertThat(dto.contents()).isEmpty();
    }

    @Test
    void create_existingName_throwsConflict() {
        when(sourceRepository.existsByName("rh")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateSourceRequest("rh", null, null)))
                .isInstanceOf(SourceAlreadyExistsException.class);
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void update_changesDescriptionAndQuestionsAndReturnsContents() {
        var entity = source("rh");
        when(sourceRepository.findByName("rh")).thenReturn(Optional.of(entity));
        when(sourceRepository.save(entity)).thenReturn(entity);
        when(userService.getUuid("jdoe")).thenReturn("uuid-1");
        when(documentRepository.findDistinctContentsBySourceAndUser("rh", "uuid-1")).thenReturn(List.of(content("T", null)));

        var dto = service.update("rh", new UpdateSourceRequest("Nouvelle", null), "jdoe");

        assertThat(entity.getDescription()).isEqualTo("Nouvelle");
        assertThat(entity.getHypotheticalQuestions()).isEmpty();
        assertThat(dto.contents()).containsExactly(new SourceContentDto("T", null));
    }

    @Test
    void update_unknown_throwsNotFound() {
        when(sourceRepository.findByName("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("x", new UpdateSourceRequest(null, null), "jdoe"))
                .isInstanceOf(SourceNotFoundException.class);
    }

    @Test
    void delete_cascadesToChunksAndTemporaryDocumentsOfTheSource() {
        var entity = source("rh");
        when(sourceRepository.findByName("rh")).thenReturn(Optional.of(entity));
        when(documentRepository.findDistinctTitlesBySource("rh")).thenReturn(List.of("a.pdf", "b.pdf"));

        service.delete("rh");

        var order = inOrder(documentRepository, tempRepository, sourceRepository);
        // Les titres sont lus avant la suppression des chunks dont ils sont dérivés.
        order.verify(documentRepository).findDistinctTitlesBySource("rh");
        order.verify(documentRepository).deleteBySource("rh");
        order.verify(tempRepository).deleteByFileNameIn(List.of("a.pdf", "b.pdf"));
        order.verify(sourceRepository).delete(entity);
    }

    @Test
    void delete_withoutContents_skipsTemporaryDocuments() {
        var entity = source("rh");
        when(sourceRepository.findByName("rh")).thenReturn(Optional.of(entity));
        when(documentRepository.findDistinctTitlesBySource("rh")).thenReturn(List.of());

        service.delete("rh");

        verify(tempRepository, never()).deleteByFileNameIn(anyList());
        verify(sourceRepository).delete(entity);
    }

    @Test
    void delete_unknown_throwsWithoutDeletingAnything() {
        when(sourceRepository.findByName("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("x")).isInstanceOf(SourceNotFoundException.class);
        verify(documentRepository, never()).deleteBySource(any());
    }

    private static SourceEntity source(String name) {
        var entity = new SourceEntity();
        entity.setId(1L);
        entity.setName(name);
        return entity;
    }

    private static DocumentRepository.SourceContentProjection content(String title, String url) {
        return new DocumentRepository.SourceContentProjection() {
            @Override public String getTitle() { return title; }
            @Override public String getUrl() { return url; }
        };
    }
}
