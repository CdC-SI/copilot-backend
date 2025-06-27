package zas.admin.zec.backend.agent.tools.ii.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.agent.tools.ii.model.DataPoint;
import zas.admin.zec.backend.agent.tools.ii.model.Gender;
import zas.admin.zec.backend.agent.tools.ii.model.TableKind;
import zas.admin.zec.backend.agent.tools.ii.repository.DataRepository;

import java.time.Year;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndexationService {

    private final DataRepository repo;

    private final Map<TableKind,
            Map<String, Map<String, Double>>> cache = new EnumMap<>(TableKind.class);

    public double index(Gender gender, String branchId, Year year) {
        TableKind kind = switch (gender) {
            case MALE   -> TableKind.T1_MALE;
            case FEMALE -> TableKind.T1_FEMALE;
            case RAI -> TableKind.T1_RAI;
        };

        Map<String, Map<String, Double>> table =
                cache.computeIfAbsent(kind, this::load);

        return Optional.ofNullable(table.get(branchId))
                .map(m -> m.get(year.toString()))
                .orElseThrow(() ->
                        new IllegalArgumentException("Indice manquant pour %s/%s/%s"
                                .formatted(gender, branchId, year)));
    }

    private Map<String, Map<String, Double>> load(TableKind k) {
        return repo.loadTables().get(k.name()).stream()
                .collect(Collectors.toMap(DataPoint::id, DataPoint::indexValues));
    }
}

