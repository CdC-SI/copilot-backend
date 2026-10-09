package zas.admin.zec.backend.rag;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RAGPromptsProcessDirectiveTest {

    @ParameterizedTest
    @CsvSource({"fr,<consigne_processus>", "de,<anweisung_prozess>", "it,<istruzione_processo>"})
    void directiveIsLocalizedAndListsProcesses(String lang, String openingTag) {
        String directive = RAGPrompts.getProcessDirective(lang, "- CSC AF - Traiter un exemple");

        assertTrue(directive.contains(openingTag));
        assertTrue(directive.contains("- CSC AF - Traiter un exemple"));
        assertTrue(directive.contains("get_business_process"));
        assertTrue(directive.contains("Brouillon"));
        assertFalse(directive.contains("%s"));
    }
}
