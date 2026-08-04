package zas.admin.zec.backend.rag.token;

import java.util.Map;

/**
 * Token remonté au frontend pour indiquer le workspace effectivement utilisé pour répondre à la
 * question (connu dès le départ, ou inféré par {@link zas.admin.zec.backend.tools.RAGTool} quand
 * aucun workspace n'était présent dans le contexte).
 */
public final class WorkspaceToken implements Token {

    public static final String KEY_NAME = "name";

    private final String name;

    public WorkspaceToken(String name) {
        this.name = name != null ? name : "";
    }

    @Override
    public String content() {
        return "<workspace><name>%s</name></workspace>".formatted(name);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of(KEY_NAME, name);
    }

    public String name() {
        return name;
    }
}
