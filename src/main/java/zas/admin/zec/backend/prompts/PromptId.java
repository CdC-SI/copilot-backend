package zas.admin.zec.backend.prompts;

import java.util.List;

/**
 * Prompts LLM externalisés dans {@code src/main/resources/prompts/<langue>/<clé>.txt} (fr, de, it), avec les
 * variables attendues par chacun.
 */
public enum PromptId {

    /** Prompt système de l'agent RAG ({@code RAGChatService}). */
    AGENTIC_SYSTEM("agentic-system", Syntax.NAMED, "response_format"),
    /** Prompt système des conversations sans RAG ({@code AttachmentChatService}). */
    ATTACHMENT_SYSTEM("attachment-system", Syntax.NAMED, "response_format"),
    /** Consigne de recherche obligatoire lorsque l'utilisateur a choisi le workspace. */
    FORCED_RETRIEVAL_DIRECTIVE("forced-retrieval-directive", Syntax.NAMED, "workspace"),
    /** Classification de la question parmi les workspaces ({@code RAGTool}). */
    WORKSPACE_INFERENCE("workspace-inference", Syntax.NAMED, "workspaces", "default_workspace"),
    /** Génération du titre d'une nouvelle conversation. */
    CONVERSATION_TITLE("conversation-title", Syntax.NAMED, "answer"),
    /** Résumé incrémental des tours anciens d'une conversation (mémoire conversationnelle). */
    CONVERSATION_SUMMARY("conversation-summary", Syntax.NAMED, "previous_summary", "turns"),
    /** Réécriture de la requête de recherche ({@code RewriteQueryTransformer}). */
    QUERY_REWRITER("query-rewriter", Syntax.SPRING_AI, "query", "target"),
    /** Expansion de la requête de recherche ({@code MultiQueryExpander}). */
    QUERY_EXPANDER("query-expander", Syntax.SPRING_AI, "query", "number");

    /** Syntaxe des variables d'un prompt. */
    public enum Syntax {
        /** {@code {{nom}}}, rendu par {@link Prompt#render}. */
        NAMED,
        /** {@code {nom}}, rendu par Spring AI ({@code PromptTemplate}) : le texte est transmis brut. */
        SPRING_AI
    }

    private final String key;
    private final Syntax syntax;
    private final List<String> variables;

    PromptId(String key, Syntax syntax, String... variables) {
        this.key = key;
        this.syntax = syntax;
        this.variables = List.of(variables);
    }

    /** Nom du fichier (sans extension) et clé de traçabilité. */
    public String key() {
        return key;
    }

    public Syntax syntax() {
        return syntax;
    }

    public List<String> variables() {
        return variables;
    }
}
