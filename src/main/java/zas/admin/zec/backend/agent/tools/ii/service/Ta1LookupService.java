package zas.admin.zec.backend.agent.tools.ii.service;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.agent.tools.ii.DataRepository;
import zas.admin.zec.backend.agent.tools.ii.model.DataPoint;
import zas.admin.zec.backend.agent.tools.ii.model.Gender;
import zas.admin.zec.backend.agent.tools.ii.model.TableKind;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class Ta1LookupService {

    private final Map<String, Map<String, BigDecimal>> cache;

    public Ta1LookupService(DataRepository repo) {
        cache = repo.loadTables().get(TableKind.TA1.name()).stream()
                .collect(Collectors.toMap(
                        DataPoint::id,
                        dp -> dp.indexValues().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> new BigDecimal(e.getValue().toString())))));
    }

    public BigDecimal salary(String branch, int skill, Gender g) {
        String col = skill + " " + (g == Gender.MALE ? "homme" : "femme");
        return cache.get(branch).get(col);
    }
}
