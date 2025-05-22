package zas.admin.zec.backend.agent.tools.ii.model;

import java.math.BigDecimal;
import java.time.Year;

public record Salary(
        Year referenceYear,
        BigDecimal amountMonthly) {
}
