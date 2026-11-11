package zas.admin.zec.backend.actions.catalog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.entity.SourceEntity;
import zas.admin.zec.backend.persistence.entity.WorkspaceEntity;
import zas.admin.zec.backend.persistence.repository.SourceRepository;
import zas.admin.zec.backend.persistence.repository.WorkspaceRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Import / export en masse des sources et workspaces (catalogue YAML).
 *
 * <p>L'import est un <em>upsert</em> : un élément absent est créé, un élément existant est
 * entièrement remplacé par sa définition (un champ absent devient vide). Les éléments présents
 * en base mais absents du fichier ne sont pas modifiés. L'opération est atomique : toutes les
 * vérifications sont faites avant la première écriture et tout s'exécute dans une transaction.</p>
 */
@Slf4j
@Service
public class CatalogService {

    private final SourceRepository sourceRepository;
    private final WorkspaceRepository workspaceRepository;

    public CatalogService(SourceRepository sourceRepository, WorkspaceRepository workspaceRepository) {
        this.sourceRepository = sourceRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional
    public CatalogImportReport importCatalog(CatalogFile catalog) {
        var existingSources = sourceRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(SourceEntity::getName, s -> s, (a, b) -> a, HashMap::new));

        validateReferences(catalog, existingSources.keySet());

        var sourceReport = new ReportBuilder();
        for (var entry : catalog.sources()) {
            var entity = existingSources.get(entry.name());
            if (entity == null) {
                entity = new SourceEntity();
                entity.setName(entry.name());
                applySource(entity, entry);
                existingSources.put(entry.name(), sourceRepository.save(entity));
                sourceReport.created.add(entry.name());
            } else if (sameSource(entity, entry)) {
                sourceReport.unchanged.add(entry.name());
            } else {
                applySource(entity, entry);
                existingSources.put(entry.name(), sourceRepository.save(entity));
                sourceReport.updated.add(entry.name());
            }
        }

        var workspaceReport = new ReportBuilder();
        for (var entry : catalog.workspaces()) {
            var sources = new LinkedHashSet<SourceEntity>();
            entry.sources().forEach(name -> sources.add(existingSources.get(name)));

            var existing = workspaceRepository.findByName(entry.name());
            if (existing.isEmpty()) {
                var entity = new WorkspaceEntity();
                entity.setName(entry.name());
                applyWorkspace(entity, entry, sources);
                workspaceRepository.save(entity);
                workspaceReport.created.add(entry.name());
            } else if (sameWorkspace(existing.get(), entry)) {
                workspaceReport.unchanged.add(entry.name());
            } else {
                applyWorkspace(existing.get(), entry, sources);
                workspaceRepository.save(existing.get());
                workspaceReport.updated.add(entry.name());
            }
        }

        var report = new CatalogImportReport(sourceReport.build(), workspaceReport.build());
        log.info("Import du catalogue : sources {} / workspaces {}", report.sources(), report.workspaces());
        return report;
    }

    @Transactional(readOnly = true)
    public CatalogFile exportCatalog() {
        var sources = sourceRepository.findAllByOrderByNameAsc().stream()
                .map(s -> new CatalogFile.SourceEntry(s.getName(), s.getDescription(), toList(s.getHypotheticalQuestions())))
                .toList();
        var workspaces = workspaceRepository.findAllByOrderByNameAsc().stream()
                .map(w -> new CatalogFile.WorkspaceEntry(
                        w.getName(),
                        w.getDescription(),
                        toList(w.getHypotheticalQuestions()),
                        w.getSources().stream().map(SourceEntity::getName).sorted().toList()))
                .toList();
        return new CatalogFile(CatalogFile.CURRENT_VERSION, sources, workspaces);
    }

    private static void validateReferences(CatalogFile catalog, Set<String> existingSourceNames) {
        var errors = new ArrayList<String>();

        var fileSourceNames = new HashSet<String>();
        for (int i = 0; i < catalog.sources().size(); i++) {
            var name = catalog.sources().get(i).name();
            if (!fileSourceNames.add(name)) {
                errors.add("sources[%d].name : source '%s' définie plusieurs fois".formatted(i, name));
            }
        }

        var workspaceNames = new HashSet<String>();
        for (int i = 0; i < catalog.workspaces().size(); i++) {
            var workspace = catalog.workspaces().get(i);
            if (!workspaceNames.add(workspace.name())) {
                errors.add("workspaces[%d].name : workspace '%s' défini plusieurs fois".formatted(i, workspace.name()));
            }
            var seen = new HashSet<String>();
            for (var source : workspace.sources()) {
                if (!seen.add(source)) {
                    errors.add("workspaces[%d].sources : source '%s' référencée plusieurs fois".formatted(i, source));
                } else if (!fileSourceNames.contains(source) && !existingSourceNames.contains(source)) {
                    errors.add("workspaces[%d].sources : source inconnue '%s'".formatted(i, source));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidCatalogException(errors);
        }
    }

    private static void applySource(SourceEntity entity, CatalogFile.SourceEntry entry) {
        entity.setDescription(entry.description());
        entity.setHypotheticalQuestions(entry.hypotheticalQuestions().toArray(String[]::new));
    }

    private static void applyWorkspace(WorkspaceEntity entity, CatalogFile.WorkspaceEntry entry, Set<SourceEntity> sources) {
        entity.setDescription(entry.description());
        entity.setHypotheticalQuestions(entry.hypotheticalQuestions().toArray(String[]::new));
        entity.setSources(sources);
    }

    private static boolean sameSource(SourceEntity entity, CatalogFile.SourceEntry entry) {
        return Objects.equals(entity.getDescription(), entry.description())
                && toList(entity.getHypotheticalQuestions()).equals(entry.hypotheticalQuestions());
    }

    private static boolean sameWorkspace(WorkspaceEntity entity, CatalogFile.WorkspaceEntry entry) {
        var currentSources = entity.getSources().stream().map(SourceEntity::getName).collect(Collectors.toSet());
        return Objects.equals(entity.getDescription(), entry.description())
                && toList(entity.getHypotheticalQuestions()).equals(entry.hypotheticalQuestions())
                && currentSources.equals(Set.copyOf(entry.sources()));
    }

    private static List<String> toList(String[] values) {
        return values != null ? Arrays.asList(values) : List.of();
    }

    private static final class ReportBuilder {
        private final List<String> created = new ArrayList<>();
        private final List<String> updated = new ArrayList<>();
        private final List<String> unchanged = new ArrayList<>();

        CatalogImportReport.ItemReport build() {
            return new CatalogImportReport.ItemReport(created, updated, unchanged);
        }
    }
}
