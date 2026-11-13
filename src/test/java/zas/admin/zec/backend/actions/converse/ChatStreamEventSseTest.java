package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** T1 : format SSE des événements du flux de réponse (contrat avec le frontend). */
class ChatStreamEventSseTest {

    @Test
    @DisplayName("le type devient le nom de l'événement SSE et data est sérialisé en JSON")
    void eventsAreSerializedAsNamedJsonEvents() {
        assertSse(ChatStreamEvent.start("conv", "msg"), "start", "{\"conversationId\":\"conv\",\"messageId\":\"msg\"}");
        assertSse(ChatStreamEvent.status("retrieval", "Recherche"), "status", "{\"code\":\"retrieval\",\"label\":\"Recherche\"}");
        assertSse(ChatStreamEvent.textDelta("Bon\njour"), "text_delta", "{\"text\":\"Bon\\njour\"}");
        assertSse(ChatStreamEvent.suggestion("Et ?"), "suggestion", "{\"text\":\"Et ?\"}");
        assertSse(ChatStreamEvent.workspace("AVS"), "workspace", "{\"name\":\"AVS\"}");
        assertSse(ChatStreamEvent.done("msg", true), "done", "{\"messageId\":\"msg\",\"interrupted\":true}");
        assertSse(ChatStreamEvent.error("GENERATION_FAILED", "boom"), "error", "{\"code\":\"GENERATION_FAILED\",\"message\":\"boom\"}");
    }

    @Test
    @DisplayName("source : même forme que les sources de l'historique")
    void sourceUsesHistoryShape() {
        var source = new Source("doc-1", SourceType.FILE, "Mémento.pdf", "2", "", "in_force", null);

        assertSse(ChatStreamEvent.source(source), "source", "{\"documentId\":\"doc-1\",\"type\":\"FILE\",\"link\":\"Mémento.pdf\","
                + "\"pageNumber\":\"2\",\"subsection\":\"\",\"version\":\"in_force\",\"title\":null}");
    }

    private static void assertSse(ChatStreamEvent event, String name, String json) {
        var sse = ConversationController.toServerSentEvent(event);
        assertEquals(name, sse.event());
        assertEquals(json, sse.data());
    }
}
