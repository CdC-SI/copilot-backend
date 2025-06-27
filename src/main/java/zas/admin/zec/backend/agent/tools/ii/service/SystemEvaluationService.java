package zas.admin.zec.backend.agent.tools.ii.service;

import zas.admin.zec.backend.agent.tools.ii.model.*;

import java.util.Comparator;
import java.util.List;

public class SystemEvaluationService {

    private final List<DecisionRule> rules;

    public SystemEvaluationService(List<DecisionRule> rules) {
        this.rules = rules;
    }

    public Outcome evaluate(SystemEvaluation evaluation) {
        // Look for a 100% match first
        for (DecisionRule rule : rules) {
            if (rule.matches(evaluation)) {
                List<String> rationale = rule.steps().stream()
                        .map(step -> "- " + step.questionLabel()
                                + " → " + (step.expectedYes() ? "Oui" : "Non"))
                        .toList();

                return new DecisionOutcome(rule.id(), rule.decision(), rationale, rule.sources());
            }
        }

        // If no 100% match, look for a follow-up question
        var best = rules.stream()
                .filter(rule -> rule.score(evaluation) >= 0)
                .max(Comparator.comparingInt(rule -> rule.score(evaluation)));

        // If we found a rule with some matching steps, return the first missing question
        // Otherwise, return a fallback message (the evaluation has no matching rules)
        return best.map(decisionRule -> decisionRule.firstMissing(evaluation)
                    .<Outcome>map(step -> new FollowUpOutcome(decisionRule.id(), step.questionLabel()))
                    .orElse(new FollowUpOutcome(decisionRule.id(), "Aucune question de suivi disponible, essayez de donner plus de détails.")))
                .orElseGet(() -> new NoDecisionMatchingOutcome(evaluation.questionAnswers()));

    }
}
