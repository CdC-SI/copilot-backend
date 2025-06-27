package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.List;

public record DecisionOutcome(int ruleId,
                              String decision,
                              List<String> sources,
                              List<String> rationale) implements Outcome {}
