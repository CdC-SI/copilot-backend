package zas.admin.zec.backend.agent.tools.ii.service;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.agent.tools.ii.model.DataPoint;
import zas.admin.zec.backend.agent.tools.ii.model.TableKind;
import zas.admin.zec.backend.agent.tools.ii.repository.DataRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class HoursService {

    private static final int WEEKLY_HOURS_BASE = 40;

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

    public BigDecimal annualSalary(Year yearOfEligibility, BigDecimal monthlySalary, String branchId) {
        return monthlySalary
                .multiply(BigDecimal.valueOf(weeklyHours(branchId, yearOfEligibility)))
                .divide(BigDecimal.valueOf(WEEKLY_HOURS_BASE), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(12));
    }
}

