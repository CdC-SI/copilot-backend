package zas.admin.zec.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import zas.admin.zec.backend.agent.tools.ii.model.DecisionRule;
import zas.admin.zec.backend.agent.tools.ii.model.SystemEvaluation;
import zas.admin.zec.backend.agent.tools.ii.service.SystemEvaluationService;
import zas.admin.zec.backend.agent.tools.ii.utils.DecisionRuleLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
public class DecisionTreeConfig {

    @Bean
    public Map<String, Function<SystemEvaluation, Boolean>> getters() {
        return Map.ofEntries(
                Map.entry("S'agit-il d'une révision (sur demande ou d'office)?",
                        SystemEvaluation::revisionCase),
                Map.entry("Droit ouvert dans le système linéaire?",
                        SystemEvaluation::linearEntitlement),
                Map.entry("Y a-t-il eu une modification des faits entre le 01.01.2022 et le 31.12.2023?",
                        SystemEvaluation::factsChanged),
                Map.entry("Y a-t-il eu une modification d'au moins 5% du degré d'invalidité?",
                        SystemEvaluation::invalidityDelta5),
                Map.entry("Y a-t-il une augmentation du degré d'invalidité?",
                        SystemEvaluation::invalidityIncrease),
                Map.entry("Y a-t-il eu une augmentation du taux depuis le 01.01.2024?",
                        SystemEvaluation::rateUpSince2024),
                Map.entry("Y a-t-il un changement de palier selon l'ancien système?",
                        SystemEvaluation::legacyTierChange),
                Map.entry("L'âge de l’assuré, au 01.01.2022, est-il égal ou supérieur (=>) à 55 ans?",
                        SystemEvaluation::age55On2022),
                Map.entry("Y a-t-il une diminution du montant de la rente?",
                        SystemEvaluation::pensionDecrease),
                Map.entry("Le taux d'invalidité est-il d'au-moins 70%?",
                        SystemEvaluation::invalidity70Plus),
                Map.entry("Le taux d'invalidité est-il d'au-moins 50%?",
                        SystemEvaluation::invalidity50Plus),
                Map.entry("Y a-t-il une augmentation du montant de la rente?",
                        SystemEvaluation::pensionIncrease),
                Map.entry("S'agit-il d'une 1ère demande RER ou demande subséquente déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022?",
                        SystemEvaluation::rerBeforeJul2021)
        );
    }

    @Bean
    public List<DecisionRule> decisionRules(@Value("classpath:decision_tree.json") Resource treeJson,
                                            ObjectMapper mapper,
                                            Map<String, Function<SystemEvaluation, Boolean>> getters) throws IOException {

        String json = new String(treeJson.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return DecisionRuleLoader.loadRules(mapper, json, getters);
    }

    @Bean
    public SystemEvaluationService evaluationEngine(List<DecisionRule> decisionRules) {
        return new SystemEvaluationService(decisionRules);
    }
}
