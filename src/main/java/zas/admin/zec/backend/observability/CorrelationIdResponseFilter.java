package zas.admin.zec.backend.observability;

import ch.admin.zas.jweb.webstarter.observability.CorrelationIdFilter;
import io.micrometer.common.KeyValue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.servlet.filter.OrderedFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;

import java.io.IOException;

/**
 * Complète le {@link CorrelationIdFilter} de jweb (exécuté juste avant) : renvoie l'identifiant de corrélation dans
 * l'en-tête de réponse {@value CorrelationId#HEADER} et l'ajoute comme attribut ({@value #SPAN_ATTRIBUTE}) du span
 * de la requête, pour retrouver une trace à partir d'un identifiant remonté par le frontend.
 */
public class CorrelationIdResponseFilter extends OncePerRequestFilter implements OrderedFilter {

    static final String SPAN_ATTRIBUTE = "zia.correlation_id";
    /** Juste après le filtre jweb (qui alimente le MDC), avant Spring Security. */
    static final int ORDER = -104 + 1;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = CorrelationId.current();
        if (correlationId != null) {
            response.setHeader(CorrelationId.HEADER, correlationId);
            ServerHttpObservationFilter.findObservationContext(request).ifPresent(context ->
                    context.addHighCardinalityKeyValue(KeyValue.of(SPAN_ATTRIBUTE, correlationId)));
        }
        chain.doFilter(request, response);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
