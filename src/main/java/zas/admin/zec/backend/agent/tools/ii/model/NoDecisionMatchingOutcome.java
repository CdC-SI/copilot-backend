package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.List;

public record NoDecisionMatchingOutcome(List<String> rationale) implements Outcome {
    @Override
    public int ruleId() {
        return -1; // No specific rule ID for no decision matching outcome
    }
}
