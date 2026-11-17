package zas.admin.zec.backend.prompts;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptRegistryTest {

    private final PromptRegistry registry = new PromptRegistry();

    @Test
    void everyPromptIsLoadedInEveryLanguage() {
        for (String language : PromptRegistry.LANGUAGES) {
            for (PromptId id : PromptId.values()) {
                Prompt prompt = registry.get(id, language);
                assertEquals(id, prompt.id());
                assertEquals(language, prompt.language());
                assertFalse(prompt.template().isBlank());
                assertFalse(prompt.template().contains("\r"));
                assertEquals(PromptRegistry.version(prompt.template()), prompt.version());
                assertEquals(12, prompt.version().length());
            }
        }
    }

    @Test
    void unsupportedOrMissingLanguageFallsBackToGerman() {
        assertSame(registry.get(PromptId.AGENTIC_SYSTEM, "de"), registry.get(PromptId.AGENTIC_SYSTEM, "en"));
        assertSame(registry.get(PromptId.AGENTIC_SYSTEM, "de"), registry.get(PromptId.AGENTIC_SYSTEM, null));
        assertEquals("agentic-system/de", registry.get(PromptId.AGENTIC_SYSTEM, null).traceKey());
    }

    @Test
    void renderSubstitutesValuesLiterallyInASinglePass() {
        Prompt prompt = new Prompt(PromptId.WORKSPACE_INFERENCE, "fr", "A {{workspaces}} B {{default_workspace}} C {{workspaces}}", "v");

        String rendered = prompt.render("workspaces", "$1 \\ {{default_workspace}}\nligne 2", "default_workspace", null);

        assertEquals("A $1 \\ {{default_workspace}}\nligne 2 B null C $1 \\ {{default_workspace}}\nligne 2", rendered);
    }

    @Test
    void renderRejectsMissingOrUnknownVariables() {
        Prompt prompt = registry.get(PromptId.WORKSPACE_INFERENCE, "fr");

        assertThrows(IllegalArgumentException.class, () -> prompt.render("workspaces", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> prompt.render(Map.of("workspaces", "x", "default_workspace", "y", "other", "z")));
    }

    @Test
    void springAiPromptsAreOnlyAvailableAsRawTemplates() {
        Prompt rewriter = registry.get(PromptId.QUERY_REWRITER, "fr");

        assertTrue(rewriter.template().contains("{query}"));
        assertTrue(rewriter.template().contains("{target}"));
        assertThrows(IllegalStateException.class, () -> rewriter.render(Map.of()));
    }

    @Test
    void validationReportsVariableMismatchesAndMalformedPlaceholders() {
        assertEquals(1, PromptRegistry.validate("p", PromptId.AGENTIC_SYSTEM, "{{response_format}} {{other}}").size());
        assertEquals(1, PromptRegistry.validate("p", PromptId.AGENTIC_SYSTEM, "{{response_format}} {{ Mal }}").size());
        assertEquals(1, PromptRegistry.validate("p", PromptId.QUERY_EXPANDER, "{query}").size());
        assertEquals(1, PromptRegistry.validate("p", PromptId.QUERY_EXPANDER, "{query} {number} {unexpected}").size());
        assertEquals(1, PromptRegistry.validate("p", PromptId.QUERY_EXPANDER, "{query} {number} {{x}}").size());
        assertTrue(PromptRegistry.validate("p", PromptId.QUERY_EXPANDER, "{query} {number}").isEmpty());
    }

    @Test
    void missingPromptFilesFailAtStartup() {
        var error = assertThrows(IllegalStateException.class, () -> new PromptRegistry("prompts-absent"));

        assertTrue(error.getMessage().contains("prompts-absent/fr/agentic-system.txt : fichier absent"), error.getMessage());
    }
}
