package zas.admin.zec.backend.actions.source;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.entity.SourceEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.SourceRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;

import java.util.List;

/**
 * Service d'administration des Sources.
 *
 * <p>Les <em>contenus</em> d'une source ne sont pas stockés : ils sont dérivés à la volée des
 * métadonnées {@code title} / {@code url} des chunks de {@code vector_store}.</p>
 */
@Slf4j
@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final DocumentRepository documentRepository;
    private final TempSourceDocumentRepository tempSourceDocumentRepository;

    public SourceService(SourceRepository sourceRepository,
                         DocumentRepository documentRepository,
                         TempSourceDocumentRepository tempSourceDocumentRepository) {
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.tempSourceDocumentRepository = tempSourceDocumentRepository;
    }

    /**
     * Liste toutes les sources (sans les contenus dérivés, pour rester léger).
     */
    public List<SourceDto> getAll() {
        return sourceRepository.findAllByOrderByNameAsc().stream()
                .map(entity -> toDto(entity, List.of()))
                .toList();
    }

    /**
     * Récupère une source par son nom, avec ses contenus dérivés de {@code vector_store}.
     */
    public SourceDto getByName(String name) {
        var entity = sourceRepository.findByName(name)
                .orElseThrow(() -> new SourceNotFoundException("Source introuvable : " + name));
        return toDto(entity, getContents(name));
    }

    @Transactional
    public SourceDto create(CreateSourceRequest request) {
        if (sourceRepository.existsByName(request.name())) {
            throw new SourceAlreadyExistsException("Une source existe déjà avec le nom : " + request.name());
        }

        var entity = new SourceEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setHypotheticalQuestions(toArray(request.hypotheticalQuestions()));

        var saved = sourceRepository.save(entity);
        log.info("Source '{}' créée (id={})", saved.getName(), saved.getId());
        return toDto(saved, List.of());
    }

    @Transactional
    public SourceDto update(String name, UpdateSourceRequest request) {
        var entity = sourceRepository.findByName(name)
                .orElseThrow(() -> new SourceNotFoundException("Source introuvable : " + name));

        entity.setDescription(request.description());
        entity.setHypotheticalQuestions(toArray(request.hypotheticalQuestions()));

        var saved = sourceRepository.save(entity);
        log.info("Source '{}' mise à jour (id={})", saved.getName(), saved.getId());
        return toDto(saved, getContents(name));
    }

    /**
     * Supprime une source et procède à la suppression en cascade :
     * <ul>
     *   <li>chunks de {@code vector_store} dont {@code metadata->>'source'} vaut {@code name} ;</li>
     *   <li>lignes {@code temp_source_document} dont le {@code file_name} correspond aux contenus
     *       (titles) dérivés de la source ;</li>
     *   <li>liens {@code workspace_source} (via la contrainte FK {@code ON DELETE CASCADE}).</li>
     * </ul>
     */
    @Transactional
    public void delete(String name) {
        var entity = sourceRepository.findByName(name)
                .orElseThrow(() -> new SourceNotFoundException("Source introuvable : " + name));

        var titles = documentRepository.findDistinctTitlesBySource(name);

        int deletedChunks = documentRepository.deleteBySource(name);
        int deletedTempDocs = titles.isEmpty() ? 0 : tempSourceDocumentRepository.deleteByFileNameIn(titles);
        sourceRepository.delete(entity);

        log.info("Source '{}' supprimée (chunks={}, temp_docs={})", name, deletedChunks, deletedTempDocs);
    }

    private List<SourceContentDto> getContents(String name) {
        return documentRepository.findDistinctContentsBySource(name).stream()
                .map(projection -> new SourceContentDto(projection.getTitle(), projection.getUrl()))
                .toList();
    }

    private SourceDto toDto(SourceEntity entity, List<SourceContentDto> contents) {
        return new SourceDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getHypotheticalQuestions() != null ? List.of(entity.getHypotheticalQuestions()) : List.of(),
                contents,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static String[] toArray(List<String> values) {
        return values != null ? values.toArray(String[]::new) : new String[0];
    }
}
