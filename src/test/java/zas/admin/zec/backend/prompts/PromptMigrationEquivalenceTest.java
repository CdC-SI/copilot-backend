package zas.admin.zec.backend.prompts;

import org.junit.jupiter.api.Test;
import zas.admin.zec.backend.actions.converse.ConversationPrompts;
import zas.admin.zec.backend.rag.RAGPrompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Temporaire : les fichiers de prompts rendent exactement les anciennes constantes Java. */
class PromptMigrationEquivalenceTest {

    private static final String MULTI_LINE = "- AFAC : allocations familiales\n- RH : ressources humaines $1 \\ 100%";

    private final PromptRegistry registry = new PromptRegistry();

    @Test
    void filesRenderTheFormerJavaConstantsByteForByte() {
        for (String lang : new String[]{"fr", "de", "it", "en"}) {
            assertEquals(RAGPrompts.getAgenticSystemPrompt(lang).formatted("COMPLETE"),
                    registry.get(PromptId.AGENTIC_SYSTEM, lang).render("response_format", "COMPLETE"), lang);
            assertEquals(RAGPrompts.getAttachmentSystemPrompt(lang).formatted((Object) null),
                    registry.get(PromptId.ATTACHMENT_SYSTEM, lang).render("response_format", null), lang);
            assertEquals(RAGPrompts.getForcedRetrievalDirective(lang, "CSC"),
                    registry.get(PromptId.FORCED_RETRIEVAL_DIRECTIVE, lang).render("workspace", "CSC"), lang);
            assertEquals(RAGPrompts.getWorkspaceInferenceTemplate(lang).formatted(MULTI_LINE, "DEFAULT"),
                    registry.get(PromptId.WORKSPACE_INFERENCE, lang).render("workspaces", MULTI_LINE,
                            "default_workspace", "DEFAULT"), lang);
            assertEquals(ConversationPrompts.getConversationTitlePrompt(lang).formatted(MULTI_LINE),
                    registry.get(PromptId.CONVERSATION_TITLE, lang).render("answer", MULTI_LINE), lang);
            assertEquals(RAGPrompts.getQueryRewriterTemplate(lang), registry.get(PromptId.QUERY_REWRITER, lang).template(), lang);
            assertEquals(RAGPrompts.getQueryExpanderTemplate(lang), registry.get(PromptId.QUERY_EXPANDER, lang).template(), lang);
        }
    }
}
