package zas.admin.zec.backend.agent.tools.ii.service;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.agent.tools.ii.DataRepository;
import zas.admin.zec.backend.agent.tools.ii.model.DataPoint;
import zas.admin.zec.backend.agent.tools.ii.model.TableKind;

import java.time.Year;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class HoursService {

    private final Map<String, Map<String, Double>> thTable;

    public HoursService(DataRepository repo) {
        thTable = repo.loadTables().get(TableKind.TH.name()).stream()
                .collect(Collectors.toMap(DataPoint::id, DataPoint::indexValues));
    }

    public double weeklyHours(String branchId, Year year) {
        return Optional.ofNullable(thTable.get(branchId))
                .map(m -> m.get(year.toString()))
                .orElseThrow();
    }
}

