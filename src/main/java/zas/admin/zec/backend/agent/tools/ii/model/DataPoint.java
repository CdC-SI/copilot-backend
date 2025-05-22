package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.Map;

public record DataPoint(String id, String label, Map<String, Double> indexValues) {
}
