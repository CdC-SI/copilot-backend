package zas.admin.zec.backend.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SettingType {
    SOURCE("sources"),
    LLM_MODEL("llm_models"),
    TAG("tags"),
    RETRIEVAL_METHOD("retrieval_methods"),
    RESPONSE_STYLE("response_style"),
    RESPONSE_FORMAT("response_format"),
    AUTHORIZED_COMMANDS("authorized_commands"),
    PROJECT_VERSION("project_version"),
    ORGANIZATION("organization");

    private final String name;
}
