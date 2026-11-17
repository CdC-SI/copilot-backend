package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** F3b : cycle de vie des générations en cours. */
class GenerationRegistryTest {

    private static final String USER = "user";
    private static final String CONVERSATION = "conv";

    private final GenerationRegistry registry = new GenerationRegistry(Duration.ofMillis(300));

    @Test
    @DisplayName("une réponse en cours de production bloque une nouvelle génération sur la même conversation (409)")
    void begin_whileStreaming_isRejected() {
        registry.begin("m1", USER, CONVERSATION);

        var conflict = assertThrows(ConversationConflictException.class, () -> registry.begin("m2", USER, CONVERSATION));

        assertEquals(ConversationConflictException.GENERATION_IN_PROGRESS, conflict.getCode());
        assertDoesNotThrow(() -> registry.begin("m3", USER, "other"));
        assertDoesNotThrow(() -> registry.begin("m4", "other", CONVERSATION));
    }

    @Test
    @DisplayName("un enregistrement en cours est attendu, puis la nouvelle génération est acceptée")
    void begin_whileSaving_waitsForFinish() throws Exception {
        var first = registry.begin("m1", USER, CONVERSATION);
        registry.saving(first);

        var second = CompletableFuture.supplyAsync(() -> registry.begin("m2", USER, CONVERSATION));
        Thread.sleep(100);
        assertFalse(second.isDone());

        registry.finish(first);
        assertEquals("m2", second.get(1, TimeUnit.SECONDS).messageId());
    }

    @Test
    @DisplayName("un enregistrement qui ne se termine pas à temps : 409")
    void begin_whileSaving_timesOut() {
        registry.saving(registry.begin("m1", USER, CONVERSATION));

        var conflict = assertThrows(ConversationConflictException.class, () -> registry.begin("m2", USER, CONVERSATION));

        assertEquals(ConversationConflictException.GENERATION_IN_PROGRESS, conflict.getCode());
    }

    @Test
    @DisplayName("Stop : signal émis pour le propriétaire d'une génération en cours de production uniquement")
    void stop_emitsSignalForOwnerOnly() {
        var generation = registry.begin("m1", USER, CONVERSATION);

        assertFalse(registry.stop("m1", "other", CONVERSATION));
        assertFalse(registry.stop("m1", USER, "other"));
        assertFalse(registry.stop("unknown", USER, CONVERSATION));
        assertFalse(generation.stopRequested());

        assertTrue(registry.stop("m1", USER, CONVERSATION));
        assertTrue(generation.stopRequested());
        assertDoesNotThrow(() -> generation.stopSignal().block(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("Stop : refusé pendant l'enregistrement et après la fin")
    void stop_afterStreaming_isRejected() {
        var generation = registry.begin("m1", USER, CONVERSATION);
        registry.saving(generation);
        assertFalse(registry.stop("m1", USER, CONVERSATION));

        registry.finish(generation);
        assertFalse(registry.stop("m1", USER, CONVERSATION));
    }

    @Test
    @DisplayName("awaitSaved : attend les enregistrements en cours, pas les réponses en production")
    void awaitSaved_waitsForSavingOnly() throws Exception {
        registry.begin("m1", USER, "streaming");
        long start = System.nanoTime();
        registry.awaitSaved(USER, "streaming");
        assertTrue(System.nanoTime() - start < Duration.ofMillis(200).toNanos());

        var saving = registry.begin("m2", USER, CONVERSATION);
        registry.saving(saving);
        var awaited = CompletableFuture.runAsync(() -> registry.awaitSaved(USER, CONVERSATION));
        Thread.sleep(100);
        assertFalse(awaited.isDone());
        registry.finish(saving);
        awaited.get(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("awaitSaved : un enregistrement qui ne se termine pas à temps : 409")
    void awaitSaved_timesOut() {
        registry.saving(registry.begin("m1", USER, CONVERSATION));

        var conflict = assertThrows(ConversationConflictException.class, () -> registry.awaitSaved(USER, CONVERSATION));

        assertEquals(ConversationConflictException.GENERATION_IN_PROGRESS, conflict.getCode());
    }

    @Test
    @DisplayName("saving : indique si un Stop a été accepté avant le passage à l'enregistrement")
    void saving_reportsAcceptedStop() {
        var stopped = registry.begin("m1", USER, CONVERSATION);
        assertTrue(registry.stop("m1", USER, CONVERSATION));
        assertTrue(registry.saving(stopped));

        var completed = registry.begin("m2", USER, "other");
        assertFalse(registry.saving(completed));
        assertFalse(registry.stop("m2", USER, "other"));
    }

    @Test
    @DisplayName("la purge conserve les enregistrements et arrête les flux souscrits expirés")
    void purge_preservesSavingAndStopsSubscribedStreaming() {
        var shortLivedRegistry = new GenerationRegistry(Duration.ofMillis(300), Duration.ZERO);
        var saving = shortLivedRegistry.begin("m1", USER, CONVERSATION);
        shortLivedRegistry.saving(saving);
        var subscribed = shortLivedRegistry.begin("m2", USER, "subscribed");
        subscribed.markSubscribed();

        assertThrows(ConversationConflictException.class, () -> shortLivedRegistry.begin("m3", USER, CONVERSATION));
        assertDoesNotThrow(() -> shortLivedRegistry.begin("m3", USER, "new"));
        var neverSubscribedRegistry = new GenerationRegistry(Duration.ofMillis(300), Duration.ZERO);
        neverSubscribedRegistry.begin("m4", USER, CONVERSATION);
        assertDoesNotThrow(() -> neverSubscribedRegistry.begin("m5", USER, CONVERSATION));
        assertFalse(saving.stopRequested());
        assertTrue(subscribed.stopRequested());
        assertDoesNotThrow(() -> subscribed.stopSignal().block(Duration.ofSeconds(1)));
    }
}
