package zas.admin.zec.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.client.ReactorNettyClientRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import zas.admin.zec.backend.config.properties.*;

import java.time.Duration;

@Configuration
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties({ApplicationProperties.class, PyBackendProperties.class, AIAgentProperties.class,
        FAQSearchProperties.class, RerankingProperties.class, DeepLProperties.class, ProxyProperties.class})
public class WebClientConfig {
    private final PyBackendProperties pyBackendProperties;
    private final ProxyProperties proxyProperties;

    public WebClientConfig(PyBackendProperties pyBackendProperties, ProxyProperties proxyProperties) {
        this.pyBackendProperties = pyBackendProperties;
        this.proxyProperties = proxyProperties;
    }

    @Bean("pyBackendWebClient")
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .baseUrl(pyBackendProperties.getBaseUrl())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "proxy.enabled", havingValue = "true")
    public RestClient.Builder restClientBuilder(final HttpClient httpClient) {
        ReactorNettyClientRequestFactory factory = new ReactorNettyClientRequestFactory(httpClient);
        factory.setExchangeTimeout(Duration.ofSeconds(6));
        return RestClient.builder()
                .requestFactory(factory);
    }

    @Bean
    @ConditionalOnProperty(name = "proxy.enabled", havingValue = "true")
    public WebClient.Builder webClientBuilder(final HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    @ConditionalOnProperty(name = "proxy.enabled", havingValue = "true")
    public HttpClient httpClient() {
        return HttpClient.create()
                .proxy(proxy -> proxy
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(proxyProperties.host())
                        .port(proxyProperties.port())
                        .nonProxyHosts(proxyProperties.nonProxyHosts())
                );
    }

    @Bean(name = "asyncExecutor")
    public TaskExecutor asyncExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("AsyncExecutor-");
        executor.initialize();
        return executor;
    }
}
