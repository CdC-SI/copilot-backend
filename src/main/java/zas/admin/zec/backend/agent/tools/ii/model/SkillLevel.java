package zas.admin.zec.backend.agent.tools.ii.model;

import lombok.Getter;

@Getter
public enum SkillLevel {
    LEVEL_1(1), LEVEL_2(2), LEVEL_3(3), LEVEL_4(4);

    private final int level;

    SkillLevel(int level) {
        this.level = level;
    }
}
