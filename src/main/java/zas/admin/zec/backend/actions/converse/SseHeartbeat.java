package zas.admin.zec.backend.actions.converse;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Maintient un flux SSE actif pendant ses silences : un commentaire ({@code :heartbeat}), ignoré par les clients SSE,
 * est intercalé à intervalle régulier jusqu'à la fin du flux.
 */
final class SseHeartbeat {

    static final String COMMENT = "heartbeat";

    private SseHeartbeat() {
    }

    static <T> Flux<ServerSentEvent<T>> withHeartbeat(Flux<ServerSentEvent<T>> events, Duration interval) {
        return withHeartbeat(events, interval, Schedulers.parallel());
    }

    static <T> Flux<ServerSentEvent<T>> withHeartbeat(Flux<ServerSentEvent<T>> events, Duration interval, Scheduler scheduler) {
        return Flux.defer(() -> {
            Sinks.Empty<Void> finished = Sinks.empty();
            Flux<ServerSentEvent<T>> heartbeats = Flux.interval(interval, interval, scheduler)
                    .map(tick -> ServerSentEvent.<T>builder().comment(COMMENT).build())
                    .takeUntilOther(finished.asMono());
            return events.doFinally(signal -> finished.tryEmitEmpty()).mergeWith(heartbeats);
        });
    }
}
