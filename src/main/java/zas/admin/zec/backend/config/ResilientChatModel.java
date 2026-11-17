package zas.admin.zec.backend.config;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Décorateur de résilience du modèle de chat (B4).
 *
 * <ul>
 *   <li><b>Nouvelles tentatives</b> avec backoff exponentiel sur erreur transitoire (5xx, 408, 409, 429, réseau,
 *       absence de premier token dans le délai), <b>uniquement avant le premier token</b> d'un flux : une réponse
 *       entamée n'est jamais rejouée.</li>
 *   <li><b>Circuit breaker</b> sur le modèle principal : seules les erreurs transitoires comptent comme échecs.</li>
 *   <li><b>Bascule</b> vers le modèle de repli (optionnel) si les tentatives sont épuisées ou si le circuit est ouvert,
 *       jamais en cours de flux. Les options de la requête sont conservées, sauf le modèle et l'effort de
 *       raisonnement, remplacés par ceux du repli.</li>
 * </ul>
 * Les autres erreurs (requête invalide, authentification…) sont propagées telles quelles.
 */
@Slf4j
public class ResilientChatModel implements ChatModel {

    private final ChatModel primary;
    private final ChatModel fallback;
    private final String fallbackModel;
    private final String fallbackReasoningEffort;
    private final CircuitBreaker circuitBreaker;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Duration firstTokenTimeout;
    private final Counter retries;
    private final Counter fallbacks;

    /**
     * @param fallback      modèle de repli, {@code null} si aucun
     * @param fallbackModel nom du modèle de repli (remplace celui des options de la requête)
     */
    public ResilientChatModel(ChatModel primary, ChatModel fallback, String fallbackModel,
                              String fallbackReasoningEffort, CircuitBreaker circuitBreaker, int maxAttempts,
                              Duration initialBackoff, Duration maxBackoff, Duration firstTokenTimeout,
                              MeterRegistry meterRegistry) {
        this.primary = primary;
        this.fallback = fallback;
        this.fallbackModel = fallbackModel;
        this.fallbackReasoningEffort = fallbackReasoningEffort == null || fallbackReasoningEffort.isBlank()
                ? null : fallbackReasoningEffort;
        this.circuitBreaker = circuitBreaker;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.firstTokenTimeout = firstTokenTimeout;
        this.retries = Counter.builder("zia.llm.retries")
                .description("Nouvelles tentatives d'appel au modèle de chat principal")
                .tag("name", circuitBreaker.getName())
                .register(meterRegistry);
        this.fallbacks = Counter.builder("zia.llm.fallbacks")
                .description("Bascules vers le modèle de repli")
                .tag("name", circuitBreaker.getName())
                .register(meterRegistry);
    }

    /** Erreur transitoire : justifie une nouvelle tentative, compte dans le circuit breaker. */
    static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof OpenAIServiceException service) {
                int status = service.statusCode();
                return status == 408 || status == 409 || status == 429 || status >= 500;
            }
            if (t instanceof OpenAIIoException || t instanceof OpenAIRetryableException
                    || t instanceof TimeoutException || t instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ChatOptions getOptions() {
        return primary.getOptions();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return primary.getDefaultOptions();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        RuntimeException failure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return circuitBreaker.executeSupplier(() -> primary.call(prompt));
            } catch (CallNotPermittedException e) {
                failure = e;
                break;
            } catch (RuntimeException e) {
                failure = e;
                if (!isTransient(e) || attempt == maxAttempts || !sleep(backoff(attempt))) {
                    break;
                }
                retries.increment();
                log.warn("Appel LLM en échec ({}), tentative {}/{}", e.toString(), attempt + 1, maxAttempts);
            }
        }
        if (canFallBack(failure)) {
            fallbacks.increment();
            log.warn("Bascule vers le modèle de repli {} : {}", fallbackModel, failure.toString());
            return fallback.call(fallbackPrompt(prompt));
        }
        throw failure;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return guardedStream(prompt)
                .retryWhen(Retry.backoff(maxAttempts - 1L, initialBackoff)
                        .maxBackoff(maxBackoff)
                        .filter(error -> !(error instanceof StreamInterruptedException)
                                && !(error instanceof CallNotPermittedException) && isTransient(error))
                        .doBeforeRetry(signal -> {
                            retries.increment();
                            log.warn("Flux LLM en échec avant le premier token ({}), tentative {}/{}",
                                    signal.failure().toString(), signal.totalRetries() + 2, maxAttempts);
                        })
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .onErrorResume(this::canFallBack, error -> {
                    fallbacks.increment();
                    log.warn("Bascule vers le modèle de repli {} : {}", fallbackModel, error.toString());
                    return withFirstTokenTimeout(fallback.stream(fallbackPrompt(prompt)));
                })
                // Erreur après le premier token : propagée telle quelle (pas de nouvelle tentative ni de bascule).
                .onErrorMap(StreamInterruptedException.class, Throwable::getCause);
    }

    /** Une tentative sur le modèle principal, sous le contrôle du circuit breaker (issue connue au premier token). */
    private Flux<ChatResponse> guardedStream(Prompt prompt) {
        return Flux.defer(() -> {
            if (!circuitBreaker.tryAcquirePermission()) {
                return Flux.error(CallNotPermittedException.createCallNotPermittedException(circuitBreaker));
            }
            long start = System.nanoTime();
            AtomicBoolean started = new AtomicBoolean();
            AtomicBoolean reported = new AtomicBoolean();
            return withFirstTokenTimeout(Flux.defer(() -> primary.stream(prompt)))
                    .doOnNext(response -> {
                        started.set(true);
                        if (reported.compareAndSet(false, true)) {
                            circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                        }
                    })
                    .doOnComplete(() -> {
                        if (reported.compareAndSet(false, true)) {
                            circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                        }
                    })
                    .doOnError(error -> {
                        if (reported.compareAndSet(false, true)) {
                            circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, error);
                        }
                    })
                    .doOnCancel(() -> {
                        if (reported.compareAndSet(false, true)) {
                            circuitBreaker.releasePermission();
                        }
                    })
                    .onErrorMap(error -> started.get(), StreamInterruptedException::new);
        });
    }

    private Flux<ChatResponse> withFirstTokenTimeout(Flux<ChatResponse> stream) {
        return stream.timeout(Mono.delay(firstTokenTimeout), ignored -> Mono.never());
    }

    private boolean canFallBack(Throwable error) {
        return fallback != null && !(error instanceof StreamInterruptedException)
                && (error instanceof CallNotPermittedException || isTransient(error));
    }

    /** Options de la requête conservées (outils, température…), modèle et effort de raisonnement du repli. */
    Prompt fallbackPrompt(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        ChatOptions fallbackOptions;
        if (options instanceof OpenAiChatOptions openAiOptions) {
            fallbackOptions = openAiOptions.mutate()
                    .baseUrl(null)
                    .apiKey(null)
                    .model(fallbackModel)
                    .reasoningEffort(fallbackReasoningEffort)
                    .build();
        } else if (options != null) {
            fallbackOptions = options.mutate().model(fallbackModel).build();
        } else {
            fallbackOptions = OpenAiChatOptions.builder().model(fallbackModel).build();
        }
        return prompt.mutate().chatOptions(fallbackOptions).build();
    }

    private Duration backoff(int attempt) {
        Duration delay = initialBackoff.multipliedBy(1L << Math.min(attempt - 1, 16));
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    private static boolean sleep(Duration delay) {
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Erreur survenue après le premier token : ni nouvelle tentative ni bascule. */
    private static final class StreamInterruptedException extends RuntimeException {
        private StreamInterruptedException(Throwable cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }
}
