package zas.admin.zec.backend.agent.tools.pension;

public sealed interface CalculResult permits InvalidAnticipationResult, NotEligibleResult, ReductionRateResult, SupplementResult {
    String result();
}
