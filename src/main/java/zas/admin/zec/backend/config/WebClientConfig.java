package zas.admin.zec.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientSsl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

@Slf4j
@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties({ApplicationProperties.class, FAQSearchProperties.class,
        RerankingProperties.class, DeepLProperties.class, ProxyProperties.class,
        IdentityCheckProperties.class, RetrievingProperties.class})
public class WebClientConfig {
    private final ProxyProperties proxyProperties;
    private final IdentityCheckProperties identityCheckProperties;


    public WebClientConfig(ProxyProperties proxyProperties, IdentityCheckProperties identityCheckProperties) {
        this.proxyProperties = proxyProperties;
        this.identityCheckProperties = identityCheckProperties;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "proxy.enabled", havingValue = "true")
    public RestClient.Builder proxyRestClientBuilder(final HttpClient httpClient) {
        return RestClient.builder()
                .requestFactory(new ReactorNettyClientRequestFactory(httpClient));
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "proxy.enabled", havingValue = "true")
    public WebClient.Builder proxyWebClientBuilder(final HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean(name = "clientBuilderForInternalCalls")
    public WebClient.Builder noProxyWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnProperty(name = "proxy.enabled", havingValue = "true")
    public HttpClient proxyHttpClient() {
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

    @Bean(name = "identityCheckRestClient")
    public RestClient identityCheckRestClient(RestClient.Builder builder, RestClientSsl ssl) {
        RestClient.Builder clientBuilder = builder.baseUrl(identityCheckProperties.baseUrl());
        try {
            clientBuilder.apply(ssl.fromBundle("identity-check-client"));
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

        return clientBuilder.build();
    }
}
