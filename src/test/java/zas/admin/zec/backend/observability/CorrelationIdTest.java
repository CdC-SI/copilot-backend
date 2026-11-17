package zas.admin.zec.backend.observability;

import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void webClientFilterAddsTheCurrentCorrelationId() {
        MDC.put(CorrelationId.MDC_KEY, "corr-1");
        var sent = new AtomicReference<ClientRequest>();

        CorrelationId.webClientFilter()
                .filter(ClientRequest.create(HttpMethod.POST, URI.create("http://reranker/v1/rerank")).build(),
                        request -> {
                            sent.set(request);
                            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK).build());
                        })
                .block();

        assertEquals("corr-1", sent.get().headers().getFirst(CorrelationId.HEADER));
    }

    @Test
    void webClientFilterKeepsAnExplicitHeaderAndSkipsWithoutCorrelationId() {
        var sent = new AtomicReference<ClientRequest>();
        var request = ClientRequest.create(HttpMethod.GET, URI.create("http://zia-rag/search")).build();

        CorrelationId.webClientFilter().filter(request, r -> {
            sent.set(r);
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK).build());
        }).block();
        assertFalse(sent.get().headers().containsHeader(CorrelationId.HEADER));

        MDC.put(CorrelationId.MDC_KEY, "corr-1");
        CorrelationId.webClientFilter().filter(ClientRequest.from(request).header(CorrelationId.HEADER, "explicit").build(), r -> {
            sent.set(r);
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK).build());
        }).block();
        assertEquals("explicit", sent.get().headers().getFirst(CorrelationId.HEADER));
    }

    @Test
    void restClientInterceptorAddsTheCurrentCorrelationId() throws Exception {
        MDC.put(CorrelationId.MDC_KEY, "corr-2");
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://identity-check/api"));

        CorrelationId.restClientInterceptor().intercept(request, new byte[0],
                (r, body) -> new MockClientHttpResponse(new byte[0], 200));

        assertEquals("corr-2", request.getHeaders().getFirst(CorrelationId.HEADER));
    }

    @Test
    void correlationIdFollowsReactorAcrossThreads() {
        CorrelationId.registerThreadLocalAccessor();
        CorrelationId.registerThreadLocalAccessor();
        Hooks.enableAutomaticContextPropagation();
        try {
            MDC.put(CorrelationId.MDC_KEY, "corr-3");
            String seen = Mono.fromCallable(CorrelationId::current)
                    .subscribeOn(Schedulers.boundedElastic())
                    .contextCapture()
                    .block(Duration.ofSeconds(5));
            assertEquals("corr-3", seen);
        } finally {
            Hooks.disableAutomaticContextPropagation();
        }
    }

    @Test
    void responseFilterReturnsTheHeaderAndTagsTheRequestObservation() throws Exception {
        MDC.put(CorrelationId.MDC_KEY, "corr-4");
        var request = new MockHttpServletRequest("POST", "/api/conversations");
        var observationContext = new ServerRequestObservationContext(request, new MockHttpServletResponse());
        request.setAttribute(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observationContext);
        var response = new MockHttpServletResponse();

        new CorrelationIdResponseFilter().doFilter(request, response, new MockFilterChain());

        assertEquals("corr-4", response.getHeader(CorrelationId.HEADER));
        assertEquals(KeyValue.of(CorrelationIdResponseFilter.SPAN_ATTRIBUTE, "corr-4"),
                observationContext.getHighCardinalityKeyValue(CorrelationIdResponseFilter.SPAN_ATTRIBUTE));
    }

    @Test
    void responseFilterIsOrderedRightAfterTheJwebFilter() {
        var jweb = new ch.admin.zas.jweb.webstarter.observability.CorrelationIdFilter(
                new ch.admin.zas.jweb.webstarter.observability.CorrelationIdContext());
        assertEquals(jweb.getOrder() + 1, new CorrelationIdResponseFilter().getOrder());
    }
}
