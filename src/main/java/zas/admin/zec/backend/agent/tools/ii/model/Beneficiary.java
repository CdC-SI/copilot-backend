package zas.admin.zec.backend.agent.tools.ii.model;

import java.time.Year;

public record Beneficiary(
        Year eligibilityYear,
        Gender gender,
        int activityRate,        // %
        int performanceLoss,     // %
        int additionalDeduction, // %
        String branchId,
        int skillLevelBefore,
        int skillLevelAfter,
        Salary effectiveBefore,
        Salary effectiveAfter) {
}
