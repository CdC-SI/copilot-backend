package zas.admin.zec.backend.actions.converse;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Générations de réponse en cours (F3b), en mémoire : le backend tourne sur une seule replica.
 * <p>
 * Une génération est {@link State#STREAMING STREAMING} tant que la réponse est produite, puis
 * {@link State#SAVING SAVING} pendant l'enregistrement de l'échange ; elle est retirée une fois l'échange enregistré
 * (ou abandonné en cas d'erreur). Le registre sert à :
 * <ul>
 *   <li>arrêter une génération côté serveur (Stop) ;</li>
 *   <li>refuser une seconde génération sur la même conversation tant que la première produit sa réponse ;</li>
 *   <li>attendre la fin des enregistrements en cours avant de relire ou de modifier l'historique.</li>
 * </ul>
 */
@Component
public class GenerationRegistry {

    /** Attente maximale d'un enregistrement en cours. */
    static final Duration SAVE_TIMEOUT = Duration.ofSeconds(10);
    /**
     * Âge au-delà duquel une génération est considérée abandonnée (flux jamais souscrit ou jamais terminé) : elle ne
     * bloque plus la conversation.
     */
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(30);

    public enum State { STREAMING, SAVING }

    /** Génération en cours. */
    public static final class Generation {
        private final String messageId;
        private final String userId;
        private final String conversationId;
        private final Sinks.Empty<Void> stop = Sinks.empty();
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private final long startedAt = System.nanoTime();
        private volatile State state = State.STREAMING;
        private volatile boolean stopRequested;
        private boolean subscribed;

        private Generation(String messageId, String userId, String conversationId) {
            this.messageId = messageId;
            this.userId = userId;
            this.conversationId = conversationId;
        }

        public String messageId() {
            return messageId;
        }

        public State state() {
            return state;
        }

        /** Signal émis par le Stop : la production de la réponse s'arrête, la réponse partielle est enregistrée. */
        public Mono<Void> stopSignal() {
            return stop.asMono();
        }

        public boolean stopRequested() {
            return stopRequested;
        }

        void markSubscribed() {
            synchronized (this) {
                subscribed = true;
            }
        }

        private boolean belongsTo(String userId, String conversationId) {
            return this.userId.equals(userId) && this.conversationId.equals(conversationId);
        }
    }

    private final Map<String, Generation> generations = new ConcurrentHashMap<>();
    private final Duration saveTimeout;
    private final Duration abandonedAfter;

    public GenerationRegistry() {
        this(SAVE_TIMEOUT, ABANDONED_AFTER);
    }

    GenerationRegistry(Duration saveTimeout) {
        this(saveTimeout, ABANDONED_AFTER);
    }

    GenerationRegistry(Duration saveTimeout, Duration abandonedAfter) {
        this.saveTimeout = saveTimeout;
        this.abandonedAfter = abandonedAfter;
    }

    /**
     * Enregistre une nouvelle génération. Les enregistrements en cours sur la conversation sont attendus d'abord
     * (borné par le timeout), pour que l'historique relu ensuite soit complet.
     *
     * @throws ConversationConflictException {@code GENERATION_IN_PROGRESS} si une réponse est encore produite sur la
     *                                       conversation ou si un enregistrement ne s'est pas terminé à temps
     */
    public Generation begin(String messageId, String userId, String conversationId) {
        long deadline = System.nanoTime() + saveTimeout.toNanos();
        while (true) {
            List<Generation> saving;
            synchronized (this) {
                removeAbandoned();
                var current = generationsOf(userId, conversationId);
                if (current.stream().anyMatch(g -> g.state == State.STREAMING)) {
                    throw new ConversationConflictException(ConversationConflictException.GENERATION_IN_PROGRESS);
                }
                if (current.isEmpty()) {
                    var generation = new Generation(messageId, userId, conversationId);
                    generations.put(messageId, generation);
                    return generation;
                }
                saving = current;
            }
            if (!await(saving, deadline)) {
                throw new ConversationConflictException(ConversationConflictException.GENERATION_IN_PROGRESS);
            }
        }
    }

    /**
     * Demande l'arrêt d'une génération en cours de production.
     *
     * @return {@code false} si la génération est inconnue, n'appartient pas à l'utilisateur ou n'est plus en cours
     */
    public boolean stop(String messageId, String userId, String conversationId) {
        var generation = generations.get(messageId);
        if (generation == null || !generation.belongsTo(userId, conversationId)) {
            return false;
        }
        // Atomique avec saving() : un Stop accepté est toujours pris en compte dans la réponse enregistrée.
        synchronized (generation) {
            if (generation.state != State.STREAMING) {
                return false;
            }
            generation.stopRequested = true;
        }
        generation.stop.tryEmitEmpty();
        return true;
    }

    /**
     * La réponse est produite (fin normale, Stop ou déconnexion) : l'échange est en cours d'enregistrement.
     *
     * @return {@code true} si un Stop a été accepté avant ce passage (la réponse est alors interrompue)
     */
    public boolean saving(Generation generation) {
        synchronized (generation) {
            generation.state = State.SAVING;
            return generation.stopRequested;
        }
    }

    /** Fin de la génération (échange enregistré, ou abandonné) : retirée du registre. */
    public void finish(Generation generation) {
        generations.remove(generation.messageId, generation);
        generation.finished.complete(null);
    }

    /**
     * Attend (borné) la fin des enregistrements en cours sur la conversation ; les réponses encore produites ne sont pas attendues.
     *
     * @throws ConversationConflictException {@code GENERATION_IN_PROGRESS} si un enregistrement ne s'est pas terminé à temps
     */
    public void awaitSaved(String userId, String conversationId) {
        var saving = generationsOf(userId, conversationId).stream().filter(g -> g.state == State.SAVING).toList();
        if (!await(saving, System.nanoTime() + saveTimeout.toNanos())) {
            throw new ConversationConflictException(ConversationConflictException.GENERATION_IN_PROGRESS);
        }
    }

    private void removeAbandoned() {
        long now = System.nanoTime();
        generations.values().stream()
                .filter(g -> now - g.startedAt > abandonedAfter.toNanos())
                .forEach(g -> {
                    boolean release;
                    synchronized (g) {
                        if (g.state == State.SAVING) {
                            return;
                        }
                        release = !g.subscribed;
                        if (!release) {
                            g.stopRequested = true;
                        }
                    }
                    if (release) {
                        finish(g);
                    } else {
                        g.stop.tryEmitEmpty();
                    }
                });
    }

    private List<Generation> generationsOf(String userId, String conversationId) {
        return generations.values().stream().filter(g -> g.belongsTo(userId, conversationId)).toList();
    }

    private static boolean await(List<Generation> generations, long deadline) {
        try {
            CompletableFuture.allOf(generations.stream().map(g -> g.finished).toArray(CompletableFuture[]::new))
                    .get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return true;
        }
    }
}
