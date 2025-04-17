package zas.admin.zec.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import zas.admin.zec.backend.config.properties.*;

@Configuration
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties({ApplicationProperties.class, PyBackendProperties.class,
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
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .proxy(proxy -> proxy
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(proxyProperties.host())
                        .port(proxyProperties.port())
                        .nonProxyHosts(proxyProperties.nonProxyHosts())
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
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
