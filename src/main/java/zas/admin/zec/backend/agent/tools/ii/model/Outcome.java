package zas.admin.zec.backend.agent.tools.ii.model;

public sealed interface Outcome permits DecisionOutcome, FollowUpOutcome, NoDecisionMatchingOutcome {
    int ruleId();
}

