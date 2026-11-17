package zas.admin.zec.backend.observability;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Corrélation des requêtes (T3) : en-tête de réponse et attribut de span ({@link CorrelationIdResponseFilter}),
 * propagation entre threads (context-propagation) et sur les appels sortants : {@code RestClient} construits à partir
 * du builder Spring Boot (customizer ci-dessous) ; les {@code WebClient} internes (builder
 * {@code clientBuilderForInternalCalls}, hors Boot) appliquent {@link CorrelationId#webClientFilter()}.
 */
@Configuration
public class ObservabilityConfig {

    public ObservabilityConfig() {
        CorrelationId.registerThreadLocalAccessor();
    }

    @Bean
    public CorrelationIdResponseFilter correlationIdResponseFilter() {
        return new CorrelationIdResponseFilter();
    }

    @Bean
    public RestClientCustomizer correlationIdPropagationRestClientCustomizer() {
        return builder -> builder.requestInterceptor(CorrelationId.restClientInterceptor());
    }
}
