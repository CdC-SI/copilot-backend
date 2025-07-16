package zas.admin.zec.backend.agent.tools.ii.model;

import java.math.BigDecimal;

public record IIResult(BigDecimal salaryWithoutDisability, BigDecimal salaryWithDisability, double disabilityRate) {
}
