package zas.admin.zec.backend.observability;

import ch.admin.zas.jweb.webstarter.observability.CorrelationIdFilter;
import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Identifiant de corrélation d'une requête ({@value #HEADER}).
 *
 * <p>Lu ou généré par le {@link CorrelationIdFilter} de jweb, qui le place dans le MDC (clé {@value #MDC_KEY}) le
 * temps de la requête. Pour les traitements hors du thread de la requête (flux SSE, tools exécutés sur
 * {@code boundedElastic}), la valeur MDC est enregistrée comme {@code ThreadLocalAccessor} de context-propagation :
 * capturée à la souscription et restaurée par Reactor ({@code spring.reactor.context-propagation=auto}).</p>
 *
 * <p>Propagée sur les appels HTTP sortants internes (service d'embedding, zia-rag) via
 * {@link #webClientFilter()} et {@link #restClientInterceptor()}.</p>
 */
public final class CorrelationId {

    public static final String HEADER = CorrelationIdFilter.CORRELATION_ID_HEADER_NAME;
    public static final String MDC_KEY = CorrelationIdFilter.CORRELATION;
    /** Clé du {@code ThreadLocalAccessor} dans le {@link ContextRegistry}. */
    static final String ACCESSOR_KEY = "zia.correlation-id";

    private CorrelationId() {
    }

    /** Identifiant de corrélation courant, ou {@code null} hors requête. */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? null : value;
    }

    /** Rend la valeur MDC propageable entre threads (idempotent). */
    public static void registerThreadLocalAccessor() {
        ContextRegistry registry = ContextRegistry.getInstance();
        boolean registered = registry.getThreadLocalAccessors().stream()
                .anyMatch(accessor -> ACCESSOR_KEY.equals(accessor.key()));
        if (!registered) {
            registry.registerThreadLocalAccessor(ACCESSOR_KEY, CorrelationId::current,
                    value -> MDC.put(MDC_KEY, value), () -> MDC.remove(MDC_KEY));
        }
    }

    /** Ajoute l'en-tête aux requêtes {@code WebClient} s'il est connu et absent. */
    public static ExchangeFilterFunction webClientFilter() {
        return (request, next) -> {
            String correlationId = current();
            if (correlationId == null || request.headers().containsHeader(HEADER)) {
                return next.exchange(request);
            }
            return next.exchange(ClientRequest.from(request).header(HEADER, correlationId).build());
        };
    }

    /** Ajoute l'en-tête aux requêtes {@code RestClient} s'il est connu et absent. */
    public static ClientHttpRequestInterceptor restClientInterceptor() {
        return (request, body, execution) -> {
            String correlationId = current();
            if (correlationId != null && !request.getHeaders().containsHeader(HEADER)) {
                request.getHeaders().set(HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }
}
