package zas.admin.zec.backend.agent.tools.ii.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zas.admin.zec.backend.agent.tools.ii.model.*;
import zas.admin.zec.backend.config.DecisionTreeConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemEvaluationServiceTest {

    private SystemEvaluationService service;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Resource jsonTree = new ClassPathResource("decision_tree.json");
        DecisionTreeConfig config = new DecisionTreeConfig();
        List<DecisionRule> decisionRules = config.decisionRules(jsonTree, mapper, config.getters());
        service = new SystemEvaluationService(decisionRules);
    }

    @Test
    void evaluation_with_complete_input_should_return_correct_decision() {
        SystemEvaluation evaluation = new SystemEvaluation(
                false,
                true,
                null,
                true,
                null,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                false,
                null
        );

        Outcome decision = service.evaluate(evaluation);

        assertThat(decision.ruleId()).isEqualTo(20);
        assertThat(decision).isInstanceOf(DecisionOutcome.class);
        DecisionOutcome decisionOutcome = (DecisionOutcome) decision;
        assertThat(decisionOutcome.decision()).isEqualTo("Rente linéaire");
        assertThat(decisionOutcome.sources()).containsExactly("Lettre 1 des dispositions transitoires relatives à la modification du 18 octobre 2023 (RAI)", "Ch. 9102 CIRAI");
    }

    @Test
    void evaluation_with_complete_input_should_return_correct_decision_2() {
        SystemEvaluation evaluation = new SystemEvaluation(
                false,
                true,
                null,
                false,
                true,
                true,
                true,
                true,
                false,
                null,
                true,
                false,
                null,
                null
        );

        Outcome decision = service.evaluate(evaluation);

        assertThat(decision.ruleId()).isEqualTo(26);
        assertThat(decision).isInstanceOf(DecisionOutcome.class);
        DecisionOutcome decisionOutcome = (DecisionOutcome) decision;
        assertThat(decisionOutcome.decision()).isEqualTo("Rente par pallier");
        assertThat(decisionOutcome.sources()).containsExactly("Lettre b points 1 et 2 des dispositions transitoires de la modification du 19 juin 2020 (Développement continu de l’AI)");
    }

    @Test
    void evaluation_with_incomplete_input_should_return_first_missing_step() {
        SystemEvaluation evaluation = new SystemEvaluation(
                false,
                null,
                null,
                false,
                true,
                true,
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                true
        );

        Outcome decision = service.evaluate(evaluation);

        assertThat(decision.ruleId()).isEqualTo(30);
        assertThat(decision).isInstanceOf(FollowUpOutcome.class);
        FollowUpOutcome followUpOutcome = (FollowUpOutcome) decision;
        assertThat(followUpOutcome.questionToAsk()).isEqualTo("Y a-t-il eu une augmentation du taux depuis le 01.01.2024?");
    }

    @Test
    void empty_evaluation_should_return_first_missing_step_of_the_ruleset() {
        SystemEvaluation evaluation = new SystemEvaluation(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Outcome decision = service.evaluate(evaluation);
        assertThat(decision.ruleId()).isEqualTo(1);
        assertThat(decision).isInstanceOf(FollowUpOutcome.class);
        FollowUpOutcome followUpOutcome = (FollowUpOutcome) decision;
        assertThat(followUpOutcome.questionToAsk()).isEqualTo("S'agit-il d'une révision (sur demande ou d'office)?");
    }
}
