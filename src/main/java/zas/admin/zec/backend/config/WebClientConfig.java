package zas.admin.zec.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.client.ReactorNettyClientRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import zas.admin.zec.backend.config.properties.*;

@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties({ApplicationProperties.class, FAQSearchProperties.class,
        RerankingProperties.class, DeepLProperties.class, ProxyProperties.class})
public class WebClientConfig {
    private final ProxyProperties proxyProperties;

    public WebClientConfig(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
    }

    @Bean
    @ConditionalOnProperty(name = "proxy.enabled", havingValue = "true")
    public RestClient.Builder restClientBuilder(final HttpClient httpClient) {
        return RestClient.builder()
                .requestFactory(new ReactorNettyClientRequestFactory(httpClient));
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
