package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.List;
import java.util.Optional;

public record DecisionRule(int id, List<Step> steps, String decision, List<String> sources) {

    /**
     * Score = number of steps that match the input
     */
    public int score(SystemEvaluation eval) {
        int matches = 0;
        for (Step step : steps) {
            Boolean value = step.getter().apply(eval);
            if (value == null) continue;
            if (value == step.expectedYes()) {
                matches++;
            } else {
                return -1;
            }
        }
        return matches;
    }

    /**
     * True if all steps match the input
     */
    public boolean matches(SystemEvaluation eval) {
        return score(eval) == steps.size();
    }

    public Optional<Step> firstMissing(SystemEvaluation eval) {
        return steps.stream()
                .filter(step -> step.getter().apply(eval) == null)
                .findFirst();
    }
}
