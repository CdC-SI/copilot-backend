package zas.admin.zec.backend.agent.tools.ii.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import zas.admin.zec.backend.agent.tools.ii.model.DecisionRule;
import zas.admin.zec.backend.agent.tools.ii.model.Step;
import zas.admin.zec.backend.agent.tools.ii.model.SystemEvaluation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class DecisionRuleLoader {

    private DecisionRuleLoader() {}

    public static List<DecisionRule> loadRules(ObjectMapper mapper,
                                               String json,
                                               Map<String, Function<SystemEvaluation, Boolean>> getters) throws JsonProcessingException {

        TypeReference<List<Map<String,Object>>> type = new TypeReference<>() {};
        List<Map<String,Object>> raw = mapper.readValue(json, type);

        return raw.stream().map(node -> {
            int id = Integer.parseInt((String) node.get("id")); // Just to ensure the ID is present, but we don't use it
            @SuppressWarnings("unchecked")
            List<Map<String,String>> path = (List<Map<String,String>>) node.get("path");

            List<Step> steps = path.stream().map(p -> {
                String label = p.get("question");
                boolean expectYes = "Oui".equalsIgnoreCase(p.get("answer"));
                Function<SystemEvaluation, Boolean> getter = getters.get(label);
                if (getter == null)
                    throw new IllegalStateException("No getter mapped for question: " + label);
                return new Step(label, expectYes, getter);
            }).toList();

            @SuppressWarnings("unchecked") Map<String,Object> ans = (Map<String,Object>) node.get("answer");
            String decision = (String) ans.get("decision");
            @SuppressWarnings("unchecked") List<String> sources = (List<String>) ans.get("sources");

            return new DecisionRule(id, steps, decision, sources);
        }).toList();
    }
}
