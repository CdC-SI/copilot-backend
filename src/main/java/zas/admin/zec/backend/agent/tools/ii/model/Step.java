package zas.admin.zec.backend.agent.tools.ii.model;

import java.util.function.Function;

public record Step(String questionLabel,
                   boolean expectedYes,
                   Function<SystemEvaluation, Boolean> getter) {}
