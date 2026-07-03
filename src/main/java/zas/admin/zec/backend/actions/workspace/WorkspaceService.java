package zas.admin.zec.backend.actions.workspace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.config.properties.WorkspaceProperties;
import zas.admin.zec.backend.persistence.entity.SourceEntity;
import zas.admin.zec.backend.persistence.entity.WorkspaceEntity;
import zas.admin.zec.backend.persistence.repository.SourceRepository;
import zas.admin.zec.backend.persistence.repository.WorkspaceRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service d'administration des Workspaces. Fournit également les opérations de résolution
 * (workspace &rarr; sources) qui remplacent l'ancienne configuration statique
 * {@link WorkspaceProperties}.
 */
@Slf4j
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final SourceRepository sourceRepository;
    private final WorkspaceProperties workspaceProperties;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            SourceRepository sourceRepository,
                            WorkspaceProperties workspaceProperties) {
        this.workspaceRepository = workspaceRepository;
        this.sourceRepository = sourceRepository;
        this.workspaceProperties = workspaceProperties;
    }

    public List<WorkspaceDto> getAll() {
        return workspaceRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    public WorkspaceDto getByName(String name) {
        var entity = workspaceRepository.findByName(name)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace introuvable : " + name));
        return toDto(entity);
    }

    @Transactional
    public WorkspaceDto create(CreateWorkspaceRequest request) {
        if (workspaceRepository.existsByName(request.name())) {
            throw new WorkspaceAlreadyExistsException("Un workspace existe déjà avec le nom : " + request.name());
        }

        var entity = new WorkspaceEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setHypotheticalQuestions(toArray(request.hypotheticalQuestions()));
        entity.setSources(resolveSourceEntities(request.sources()));

        var saved = workspaceRepository.save(entity);
        log.info("Workspace '{}' créé (id={}, sources={})", saved.getName(), saved.getId(), saved.getSources().size());
        return toDto(saved);
    }

    @Transactional
    public WorkspaceDto update(String name, UpdateWorkspaceRequest request) {
        var entity = workspaceRepository.findByName(name)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace introuvable : " + name));

        entity.setDescription(request.description());
        entity.setHypotheticalQuestions(toArray(request.hypotheticalQuestions()));
        entity.setSources(resolveSourceEntities(request.sources()));

        var saved = workspaceRepository.save(entity);
        log.info("Workspace '{}' mis à jour (id={}, sources={})", saved.getName(), saved.getId(), saved.getSources().size());
        return toDto(saved);
    }

    @Transactional
    public void delete(String name) {
        var entity = workspaceRepository.findByName(name)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace introuvable : " + name));

        // La suppression retire aussi les liens workspace_source (le workspace en est propriétaire) ;
        // les sources elles-mêmes ne sont pas supprimées.
        workspaceRepository.delete(entity);
        log.info("Workspace '{}' supprimé", name);
    }

    /**
     * Résout la liste des noms de sources d'un workspace. Remplace
     * {@code WorkspaceProperties.getSources().get(workspace)}.
     */
    public List<String> resolveSources(String workspace) {
        if (workspace == null || workspace.isBlank()) {
            return List.of();
        }
        return workspaceRepository.findSourceNamesByWorkspaceName(workspace);
    }

    /**
     * Retourne les noms de tous les workspaces existants.
     */
    public List<String> getAllNames() {
        return workspaceRepository.findAllByOrderByNameAsc().stream()
                .map(WorkspaceEntity::getName)
                .toList();
    }

    /**
     * Retourne le premier workspace contenant la source donnée, s'il existe.
     */
    public Optional<String> findWorkspaceBySource(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return Optional.empty();
        }
        return workspaceRepository.findWorkspaceNamesBySourceName(sourceName).stream().findFirst();
    }

    /**
     * Workspace par défaut (conservé en configuration).
     */
    public String getDefaultWorkspace() {
        return workspaceProperties.getDefaultWorkspace();
    }

    private Set<SourceEntity> resolveSourceEntities(List<String> sourceNames) {
        var resolved = new LinkedHashSet<SourceEntity>();
        if (sourceNames == null) {
            return resolved;
        }
        for (String sourceName : sourceNames) {
            var source = sourceRepository.findByName(sourceName)
                    .orElseThrow(() -> new UnknownSourceException("Source inconnue : " + sourceName));
            resolved.add(source);
        }
        return resolved;
    }

    private WorkspaceDto toDto(WorkspaceEntity entity) {
        return new WorkspaceDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getHypotheticalQuestions() != null ? List.of(entity.getHypotheticalQuestions()) : List.of(),
                entity.getSources().stream().map(SourceEntity::getName).sorted().toList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static String[] toArray(List<String> values) {
        return values != null ? values.toArray(String[]::new) : new String[0];
    }
}
