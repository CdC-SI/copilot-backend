package zas.admin.zec.backend.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** B4 : nouvelles tentatives avant le premier token, circuit breaker et bascule vers le modèle de repli. */
class ResilientChatModelTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ChatModel primary = mock(ChatModel.class);
    private final ChatModel fallback = mock(ChatModel.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Prompt prompt = new Prompt("question", OpenAiChatOptions.builder()
            .model("primary-model").reasoningEffort("low").temperature(0.0).build());

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static String text(List<ChatResponse> responses) {
        return String.join("", responses.stream().map(r -> r.getResult().getOutput().getText()).toList());
    }

    private static CircuitBreaker circuitBreaker(int minimumNumberOfCalls) {
        return CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(minimumNumberOfCalls)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(ResilientChatModel::isTransient)
                .build());
    }

    private ResilientChatModel model(ChatModel fallbackModel, CircuitBreaker circuitBreaker, Duration firstTokenTimeout) {
        return new ResilientChatModel(primary, fallbackModel, "fallback-model", "", circuitBreaker, 3,
                Duration.ofMillis(1), Duration.ofMillis(5), firstTokenTimeout, meterRegistry);
    }

    private ResilientChatModel model(ChatModel fallbackModel) {
        return model(fallbackModel, circuitBreaker(100), TIMEOUT);
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    @Test
    void streamRetriesTransientErrorsBeforeTheFirstToken() {
        var attempts = new AtomicInteger();
        when(primary.stream(any(Prompt.class))).thenAnswer(invocation -> attempts.incrementAndGet() < 3
                ? Flux.error(new IOException("connection reset"))
                : Flux.just(response("Bon"), response("jour")));

        var output = model(null).stream(prompt).collectList().block(TIMEOUT);

        assertEquals("Bonjour", text(output));
        assertEquals(3, attempts.get());
        assertEquals(2, counter("zia.llm.retries"));
    }

    @Test
    void streamIsNeverReplayedOnceStarted() {
        var attempts = new AtomicInteger();
        when(primary.stream(any(Prompt.class))).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return Flux.concat(Flux.just(response("Bon")), Flux.error(new IOException("connection reset")));
        });
        var received = new CopyOnWriteArrayList<ChatResponse>();

        var error = assertThrows(RuntimeException.class,
                () -> model(fallback).stream(prompt).doOnNext(received::add).blockLast(TIMEOUT));

        assertInstanceOf(IOException.class, error.getCause());
        assertEquals("Bon", text(received));
        assertEquals(1, attempts.get());
        verifyNoInteractions(fallback);
    }

    @Test
    void streamFallsBackWhenRetriesAreExhausted() {
        when(primary.stream(any(Prompt.class))).thenReturn(Flux.error(new IOException("down")));
        when(fallback.stream(any(Prompt.class))).thenReturn(Flux.just(response("repli")));

        var output = model(fallback).stream(prompt).collectList().block(TIMEOUT);

        assertEquals("repli", text(output));
        verify(primary, times(3)).stream(any(Prompt.class));
        var fallbackPrompt = captureFallbackPrompt();
        var options = (OpenAiChatOptions) fallbackPrompt.getOptions();
        assertEquals("fallback-model", options.getModel());
        assertNull(options.getReasoningEffort());
        assertEquals(0.0, options.getTemperature());
        assertEquals(prompt.getInstructions(), fallbackPrompt.getInstructions());
        assertEquals(1, counter("zia.llm.fallbacks"));
    }

    @Test
    void firstTokenTimeoutIsRetriedThenFallsBack() {
        when(primary.stream(any(Prompt.class))).thenReturn(Flux.never());
        when(fallback.stream(any(Prompt.class))).thenReturn(Flux.just(response("repli")));

        var output = model(fallback, circuitBreaker(100), Duration.ofMillis(50)).stream(prompt).collectList()
                .block(TIMEOUT);

        assertEquals("repli", text(output));
        verify(primary, times(3)).stream(any(Prompt.class));
    }

    @Test
    void nonTransientErrorsAreNeitherRetriedNorFallenBack() {
        when(primary.stream(any(Prompt.class))).thenReturn(Flux.error(new IllegalArgumentException("bad request")));

        assertThrows(IllegalArgumentException.class, () -> model(fallback).stream(prompt).blockLast(TIMEOUT));

        verify(primary, times(1)).stream(any(Prompt.class));
        verifyNoInteractions(fallback);
    }

    @Test
    void withoutFallbackTheLastTransientErrorIsPropagated() {
        when(primary.stream(any(Prompt.class))).thenReturn(Flux.error(new IOException("down")));

        var error = assertThrows(RuntimeException.class, () -> model(null).stream(prompt).blockLast(TIMEOUT));

        assertTrue(ResilientChatModel.isTransient(error));
        verify(primary, times(3)).stream(any(Prompt.class));
    }

    @Test
    void openCircuitGoesStraightToTheFallback() {
        var circuitBreaker = circuitBreaker(2);
        when(primary.stream(any(Prompt.class))).thenReturn(Flux.error(new IOException("down")));
        when(fallback.stream(any(Prompt.class))).thenReturn(Flux.just(response("repli")));
        var resilient = model(fallback, circuitBreaker, TIMEOUT);

        resilient.stream(prompt).blockLast(TIMEOUT);
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        clearInvocations(primary);

        assertEquals("repli", text(resilient.stream(prompt).collectList().block(TIMEOUT)));
        verifyNoInteractions(primary);
    }

    @Test
    void callRetriesThenFallsBack() {
        when(primary.call(any(Prompt.class))).thenThrow(new RuntimeException(new TimeoutException("slow")));
        when(fallback.call(any(Prompt.class))).thenReturn(response("repli"));

        assertEquals("repli", model(fallback).call(prompt).getResult().getOutput().getText());

        verify(primary, times(3)).call(any(Prompt.class));
        assertEquals(2, counter("zia.llm.retries"));
    }

    @Test
    void callSucceedsAfterATransientError() {
        when(primary.call(any(Prompt.class)))
                .thenThrow(new RuntimeException(new IOException("reset")))
                .thenReturn(response("ok"));

        assertEquals("ok", model(fallback).call(prompt).getResult().getOutput().getText());
        verifyNoInteractions(fallback);
    }

    @Test
    void callDoesNotRetryNonTransientErrors() {
        when(primary.call(any(Prompt.class))).thenThrow(new IllegalStateException("invalid"));

        assertThrows(IllegalStateException.class, () -> model(fallback).call(prompt));

        verify(primary, times(1)).call(any(Prompt.class));
        verifyNoInteractions(fallback);
    }

    @Test
    void exposesThePrimaryOptions() {
        var options = OpenAiChatOptions.builder().model("primary-model").build();
        when(primary.getOptions()).thenReturn(options);

        assertSame(options, model(fallback).getOptions());
    }

    private Prompt captureFallbackPrompt() {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(fallback).stream(captor.capture());
        return captor.getValue();
    }
}
