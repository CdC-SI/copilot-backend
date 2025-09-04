package zas.admin.zec.backend.actions.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StreamEventType {
    CREATED("response.created"),
    DELTA("response.output_text.delta"),
    COMPLETED("response.completed"),
    ERROR("response.error");

    private final String eventName;

}
