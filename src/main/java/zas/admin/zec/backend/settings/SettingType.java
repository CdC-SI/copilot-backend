package zas.admin.zec.backend.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SettingType {
    SOURCE("sources"),
    LLM_MODEL("llm_models"),
    TAG("tags"),
    RETRIEVAL_METHOD("retrieval_methods");

    private final String name;
}
