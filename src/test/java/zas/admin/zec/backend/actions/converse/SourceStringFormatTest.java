package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.Test;
import zas.admin.zec.backend.rag.token.SourceToken;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Format de persistance des sources ({@code chat_history.sources}) : {@code TYPE|link|page|subsection|version|documentId|title}.
 * Aucun champ absent ne doit être écrit ni relu comme la chaîne "null".
 */
class SourceStringFormatTest {

    @Test
    void businessProcessWithoutPageOrSectionIsWrittenWithEmptyFields() {
        var source = Source.fromToken(SourceToken.fromBusinessProcess("bp-1", "Processus A", "https://bpanda/a", "2026.03.02"));

        String raw = ConversationService.toSourceString(source);

        assertEquals("BUSINESS_PROCESS|https://bpanda/a|||2026.03.02|bp-1|Processus A", raw);
        assertFalse(raw.contains("null"));
    }

    @Test
    void businessProcessRoundTripKeepsMissingFieldsNull() {
        var source = Source.fromToken(SourceToken.fromBusinessProcess("bp-1", "Processus A", "https://bpanda/a", "2026.03.02"));

        var read = ConversationService.fromSourceString(ConversationService.toSourceString(source));

        assertEquals(SourceType.BUSINESS_PROCESS, read.type());
        assertEquals("bp-1", read.documentId());
        assertEquals("Processus A", read.title());
        assertEquals("2026.03.02", read.version());
        assertNull(read.pageNumber());
        assertNull(read.subsection());
    }

    @Test
    void urlSourceHasNoTitle() {
        var source = Source.fromToken(SourceToken.fromURLWithDetails("doc-1", "https://x/y", "3", "Section", "in_force"));

        String raw = ConversationService.toSourceString(source);
        var read = ConversationService.fromSourceString(raw);

        assertEquals("URL|https://x/y|3|Section|in_force|doc-1|", raw);
        assertNull(read.title());
        assertEquals("3", read.pageNumber());
        assertEquals("Section", read.subsection());
    }

    @Test
    void literalNullWrittenBeforeTheFixIsReadAsNull() {
        var read = ConversationService.fromSourceString(
                "BUSINESS_PROCESS|https://bpanda/a|null|null|2026.03.02|bp-1|Processus A");
        var url = ConversationService.fromSourceString("URL|https://x/y|3|Section|in_force|doc-1|null");

        assertNull(read.pageNumber());
        assertNull(read.subsection());
        assertNull(url.title());
    }

    @Test
    void legacyFormatsAreStillRead() {
        assertEquals(new Source(SourceType.URL, "https://x/y"), ConversationService.fromSourceString("https://x/y"));
        assertEquals(new Source(SourceType.FILE, "a.pdf"), ConversationService.fromSourceString("FILE:a.pdf"));
        var withoutId = ConversationService.fromSourceString("URL|https://x/y|3||");
        assertEquals("URL#https://x/y#3", withoutId.documentId());
    }
}
