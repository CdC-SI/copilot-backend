package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.config.properties.ConversationStreamProperties;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F22 : commentaires SSE intercalés pendant les silences du flux (la gateway coupe après 30 s d'inactivité). */
class SseHeartbeatTest {

    private static final Duration INTERVAL = Duration.ofMillis(20);

    @Test
    @DisplayName("flux silencieux : commentaires réguliers, puis fin avec le flux")
    void heartbeatsWhileSilent() {
        var silent = Flux.just(ServerSentEvent.builder("Bonjour").event("text_delta").build())
                .delaySubscription(INTERVAL.multipliedBy(6));

        var received = SseHeartbeat.withHeartbeat(silent, INTERVAL).collectList().block(Duration.ofSeconds(5));

        assertNotNull(received);
        var last = received.getLast();
        assertEquals("Bonjour", last.data());
        assertEquals("text_delta", last.event());
        var heartbeats = received.subList(0, received.size() - 1);
        assertTrue(heartbeats.size() >= 2, received::toString);
        heartbeats.forEach(event -> {
            assertEquals(SseHeartbeat.COMMENT, event.comment());
            assertNull(event.data());
            assertNull(event.event());
        });
    }

    @Test
    @DisplayName("flux court : événements inchangés, fin sans attendre l'intervalle")
    void shortStreamIsUnchanged() {
        var events = SseHeartbeat.withHeartbeat(
                        Flux.just(ServerSentEvent.builder("a").build(), ServerSentEvent.builder("b").build()),
                        Duration.ofMinutes(1))
                .map(ServerSentEvent::data)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(List.of("a", "b"), events);
    }

    @Test
    @DisplayName("erreur du flux : propagée")
    void errorIsPropagated() {
        var failing = Flux.<ServerSentEvent<String>>error(new IllegalStateException("boom"))
                .delaySubscription(INTERVAL.multipliedBy(3));

        var error = assertThrows(IllegalStateException.class,
                () -> SseHeartbeat.withHeartbeat(failing, INTERVAL).blockLast(Duration.ofSeconds(2)));
        assertEquals("boom", error.getMessage());
    }

    @Test
    @DisplayName("intervalle nul ou négatif refusé au démarrage")
    void invalidIntervalIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConversationStreamProperties(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ConversationStreamProperties(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> new ConversationStreamProperties(null));
    }
}
