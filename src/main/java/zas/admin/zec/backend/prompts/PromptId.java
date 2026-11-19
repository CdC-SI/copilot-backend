package zas.admin.zec.backend.prompts;

import java.util.List;

/**
 * Prompts LLM externalisés dans {@code src/main/resources/prompts/<langue>/<clé>.txt} (fr, de, it), avec les
 * variables attendues par chacun.
 */
public enum PromptId {

    /** Prompt système de l'agent RAG ({@code RAGChatService}). */
    AGENTIC_SYSTEM("agentic-system", "response_format"),
    /** Prompt système des conversations sans RAG ({@code AttachmentChatService}). */
    ATTACHMENT_SYSTEM("attachment-system", "response_format"),
    /** Consigne de recherche obligatoire lorsque l'utilisateur a choisi le workspace. */
    FORCED_RETRIEVAL_DIRECTIVE("forced-retrieval-directive", "workspace"),
    /** Classification de la question parmi les workspaces ({@code RAGTool}). */
    WORKSPACE_INFERENCE("workspace-inference", "workspaces", "default_workspace"),
    /** Génération du titre d'une nouvelle conversation. */
    CONVERSATION_TITLE("conversation-title", "answer"),
    /** Résumé incrémental des tours anciens d'une conversation (mémoire conversationnelle). */
    CONVERSATION_SUMMARY("conversation-summary", "previous_summary", "turns");

    private final String key;
    private final List<String> variables;

    /** {@code variables} : variables {@code {{nom}}} du prompt, rendues par {@link Prompt#render}. */
    PromptId(String key, String... variables) {
        this.key = key;
        this.variables = List.of(variables);
    }

    /** Nom du fichier (sans extension) et clé de traçabilité. */
    public String key() {
        return key;
    }

    public List<String> variables() {
        return variables;
    }
}
