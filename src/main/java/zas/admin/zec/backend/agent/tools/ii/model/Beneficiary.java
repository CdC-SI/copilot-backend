package zas.admin.zec.backend.agent.tools.ii.model;

import java.time.Year;

public record Beneficiary(
        Year eligibilityYear,
        Gender gender,
        BeneficiaryDetails preHealthDetails,
        BeneficiaryDetails postHealthDetails,
        int activityRate,
        int activityReduction,
        int additionalDeduction) {
}
