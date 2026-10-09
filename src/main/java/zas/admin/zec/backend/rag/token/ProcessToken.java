package zas.admin.zec.backend.rag.token;

import java.util.Map;

/**
 * Token remonté au frontend pour chaque processus métier BPMN utilisé pour répondre. Collecté par
 * {@code ConversationService} et persisté sur le message ({@code chat_history.processes}) afin que
 * le processus soit réinjecté au tour suivant (ex. réponse à une question de clarification).
 */
public final class ProcessToken implements Token {

    public static final String KEY_ID = "id";
    public static final String KEY_NAME = "name";
    public static final String KEY_URL = "url";

    private final String bpmnId;
    private final String name;
    private final String url;

    public ProcessToken(String bpmnId, String name, String url) {
        this.bpmnId = bpmnId != null ? bpmnId : "";
        this.name = name != null ? name : "";
        this.url = url != null ? url : "";
    }

    @Override
    public String content() {
        return "<process><id>%s</id><name>%s</name><url>%s</url></process>".formatted(bpmnId, name, url);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of(KEY_ID, bpmnId, KEY_NAME, name, KEY_URL, url);
    }

    public String bpmnId() {
        return bpmnId;
    }

    public String name() {
        return name;
    }

    public String url() {
        return url;
    }
}
